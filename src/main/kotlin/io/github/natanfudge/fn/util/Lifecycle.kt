@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.time.TimeSource

/**
 * Context provided to lifecycle start functions that gives access to the lifecycle being started
 * and implements [AutoClose] to allow for registering resources that should be closed when the lifecycle ends.
 *
 * This context is passed to the [start] function of a [Lifecycle] and provides access to the lifecycle
 * being started through [thisLifecycle].
 */
class LifecycleContext<P : Any, T : Any>(val thisLifecycle: Lifecycle<P, T>) : AutoClose by AutoCloseImpl()

//TOdo: CLEAR this when unbinding
private val lifecycleRegistry = mutableMapOf<String, Lifecycle<*, *>>()

interface ValueHolder<T>: ReadOnlyProperty<Any?,T> {
    /**
     * Returns the current value of this, throwing an error if it hasn't been initialized.
     *
     * Use this property when you're certain the lifecycle has been started. If you're not sure,
     * use [value] which returns null for uninitialized lifecycles.
     *
     * @throws IllegalStateException if the lifecycle hasn't been initialized
     */
    val assertValue: T

    /**
     * Returns the current value of this, or null if it hasn't been initialized.
     */
    val value: T?

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return assertValue
    }
}


/**
 * In a vacuum, a [Lifecycle] is just a function that runs to create some data, and another function that runs to clean up that data.
 *
 * However, [Lifecycle]s can be combined to form a hierarchy - whenever a parent [Lifecycle] is started or stopped, the child [Lifecycle] will also
 * be started or stopped. When this happens, the child lifecycle will have the parent's data available for use.
 *
 * [Lifecycle]s may have multiple parents, in which case they will be started and stopped when either one of them are,
 * as long as all the parents have been started already. In this case, the child will receive the data that ALL of its parents have created.
 *
 * Finally, any part of a [Lifecycle] hierarchy may be restarted arbitrarily - in which case the existing data will be reused, except for
 * new data that has been created as a result of this restart.
 *
 * This class is extremely thread-unsafe. A lifecycle hierarchy should be touched within the same thread.
 * [P] is the type of the parent(s) of this lifecycle, and [T] is the type of the root of this lifecycle.
 * Note that we don't do `Lifecycle<P,T> = MutableTree<P,T>` because that would signify everything is `<P,T>` all the way down, which is not true.
 */
class Lifecycle<P : Any, T : Any> private constructor(internal val tree: LifecycleTree): ValueHolder<T> {
    companion object {
        /**
         * Creates a new lifecycle with the specified start and stop functions.
         *
         * The [start] function is called when the lifecycle is started and receives the parent's data as input.
         * It should return the data that this lifecycle will manage.
         *
         * The [stop] function is called when the lifecycle is stopped and receives the data that was created by [start].
         *
         * The [label] is used for logging and debugging purposes to identify this lifecycle.
         *
         * The [logLevel] controls the verbosity of logs for this lifecycle.
         */
        fun <P : Any, T : Any> create(
            label: String,
            logLevel: FunLogLevel = FunLogLevel.Debug,
            stop: (T) -> Unit = {},
            start: LifecycleContext<P, T>.(P) -> T,
        ): Lifecycle<P, T> {
            val ls = lifecycleRegistry.getOrPut(label) {
                val data = LifecycleData(
                    start = start,
                    stop = stop,
                    logLevel = logLevel,
//                label = if (hotReloadIndex == 0) label else "$label (Reload #$hotReloadIndex)",
                    label = label,
                    parentState = null,
                    parentCount = 0,
                    selfState = null,
//                    throwOnFail = false,
                    childrenParentIndices = mutableListOf(),
                    closed = false
                )
                val ls = Lifecycle<P, T>(
                    MutableTreeImpl(
                        value = data,
                        children = mutableListOf()
                    )
                )
                data.lsCtx = LifecycleContext(ls)
                ls
            } as Lifecycle<P, T>
            val data = (ls.tree.value as LifecycleData<P, T>)
            // Update lambda to capture new variables
            data.start = start
            data.stop = stop

            // Allow updating label and log level on reload
            data.label = label
            data.logLevel = logLevel

            // Allow changing hierarchies (done later in bind())
            data.childrenParentIndices.clear()
            data.parentCount = 0
            ls.tree.children.clear()
            return ls
        }
    }


//    fun setThrowOnFail(throwOnFail: Boolean) {
//        tree.visit {
//            it.throwOnFail = throwOnFail
//        }
////        tree.value.throwOnFail = throwOnFail
//    }

    /**
     * Removes all children from this lifecycle and returns them as a list.
     *
     * This detaches all child lifecycles from this lifecycle, meaning they will no longer
     * be started or stopped when this lifecycle is started or stopped.
     *
     * @return A list of all removed child lifecycles
     */
    fun removeChildren(): List<Lifecycle<T, *>> {
        // Copy list
        val children = tree.children.toList().map { Lifecycle<T, Any>(it) }
        tree.children.clear()
        tree.value.childrenParentIndices.clear()
        return children
    }


    /**
     * Starts this lifecycle and all its children recursively in topological order.
     *
     * This method initializes the lifecycle by calling its [start] function with the provided [seedValue].
     * It then recursively starts all child lifecycles in the correct order, ensuring that parent lifecycles
     * are started before their children.
     *
     * If this lifecycle has multiple parents, [parentIndex] may be used to specify which parent has a new value for this lifecycle.
     * Passing null to [seedValue] would try to start the lifecycle with its existing data. It's recommended to use [restart] for this purpose.
     *
     * @throws IllegalArgumentException if [parentIndex] is invalid
     */
    fun start(seedValue: P?, parentIndex: Int = 0) {
        val startTime = TimeSource.Monotonic.markNow()
//        val time = measureTime {
        require(parentIndex == 0 || parentIndex < tree.value.parentCount)

        val order = tree.topologicalSort()
        log(tree.value.logLevel) {
            "Starting tree of ${tree.value.label} by order: ${order.joinToString("→") { it.child.label }}"
        }
        for ((parents, child) in order) {
            child as LifecycleData<Any, Any>
            val parentValues = parents.map {
                val parentValue =
                    it.value.selfState ?: error("It appears that topological sort failed - parent state is not initialized when reaching child")
                val indexForChild = it.value.childrenParentIndices[it.childIndex]
                parentValue to indexForChild
            }
            if (parents.isNotEmpty()) {
                // Start non-root nodes
                child.startSingle(parentValues)
            } else {
                val rootValues = if (seedValue != null) listOf(seedValue to parentIndex) else listOf()
                // Start root
                child.startSingle(rootValues)
            }
        }
//        }


        log(tree.value.logLevel) {
            "Started ${tree.value.label} in ${startTime.elapsedNow()} total"
        }
    }

    /**
     * Ends this lifecycle and all its children recursively, bottom up.
     *
     * This method stops all lifecycles in the hierarchy by calling their [stop] functions.
     * Children are closed before their parents to ensure proper cleanup order.
     *
     * If any resources were registered with the [LifecycleContext] during startup,
     * they will be automatically closed when this method is called.
     */
    fun end() {
        val sort = tree.topologicalSort().asReversed()
        log(tree.value.logLevel) {
            "Ending tree of ${tree.value.label} by order: ${sort.joinToString("→") { it.child.label }}"
        }
        sort.forEach {
            it.child.endSingle()
        }
    }

    /**
     * Restarts this lifecycle using its existing data.
     *
     * This is equivalent to calling [end] followed by [start] with null seedValue.
     */
    fun restart() {
        end()
        start(seedValue = null, parentIndex = 0) // Placeholder parentIndex, it won't be used
    }

    // SLOW: technically we can build a single "Restart" action for all the labels which would be faster than restarting the labels one by one.
    fun restartByLabels(labels: Set<String>) {
        tree.visitSubtrees {
            if (it.value.label in labels) Lifecycle<Any, Any>(it).restart()
        }
    }

    /**
     * Restarts this lifecycle with a specific [seedValue] for the specified parent.
     *
     * This is equivalent to calling [end] followed by [start] with the provided seedValue and parentIndex.
     * Use this when you need to restart a lifecycle with new parent data.
     *
     * The [parentIndex] specifies which parent's data to update if this lifecycle has multiple parents.
     */
    fun restart(
        seedValue: P,
        parentIndex: Int,
    ) {
        end()
        start(seedValue, parentIndex)
    }

    /**
     * Causes [child] to start and stop when `this` starts and stops.
     *
     * This establishes a parent-child relationship between this lifecycle and the [child] lifecycle.
     * When this lifecycle starts, the child will also start, and when this lifecycle stops, the child will also stop.
     *
     * The [child] will receive the data of this lifecycle when it is started.
     * If this is the only lifecycle [child] is bound to, then the data of this lifecycle will be passed directly (CP = T).
     * If [child] will be bound to more lifecycles, the child will receive a list of values, each having the value of each parent,
     * ordered by the order it was bound to its parents.
     *
     * The [runEarly] parameter controls the execution order of children. If true, the child will be inserted
     * before all other children, ensuring it runs first when this lifecycle starts.
     */
    fun <CP : Any, CT : Any> bind(child: Lifecycle<CP, CT>, runEarly: Boolean = false) {
        val parentIndex = child.tree.value.parentCount
        child.tree.value.parentCount++
        if (runEarly) {
            tree.value.childrenParentIndices.add(0, parentIndex)
            tree.children.add(0, child.tree)
        } else {
            tree.value.childrenParentIndices.add(parentIndex)
            tree.children.add(child.tree)
        }
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle.
     *
     * This is a convenience method that creates a new lifecycle with the given [label], [logLevel],
     * [start], and [stop] functions, and then binds it to this lifecycle.
     *
     * The [start] function is called when the child lifecycle starts and receives this lifecycle's data.
     * The [stop] function is called when the child lifecycle stops.
     *
     * The [early] parameter controls the execution order. If true, the child will be inserted
     * before all other children, ensuring it runs first when this lifecycle starts.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any> bind(
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        early: Boolean = false,
        start: LifecycleContext<T, CT>.(T) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create(label, logLevel, stop, start)
        bind(ls, early)
        return ls
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle and [secondLifecycle].
     *
     * This method creates a new lifecycle that depends on both this lifecycle and [secondLifecycle].
     * The child lifecycle will start when both parent lifecycles have started, and will stop when
     * either parent lifecycle stops.
     *
     * The [start] function receives the data from both parent lifecycles and should return the data
     * that the child lifecycle will manage.
     *
     * The [early1] and [early2] parameters control the execution order relative to other children
     * of the respective parent lifecycles.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any, P2 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        early1: Boolean = false,
        early2: Boolean = false,
        start: (T, P2) -> CT,
    ): Lifecycle<T, CT> {

//         val parentA = parents[0] as? T ?: error("Lifecycle $label was ran with its ${secondLifecycle.label} lifecycle but not its ${this@Lifecycle.label} lifecycle")
//            val parentB = parents[1] as? P2 ?:error("Lifecycle $label was ran with its ${this@Lifecycle.label} lifecycle but not its ${secondLifecycle.label} lifecycle")
//
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            start(parentA, parentB)
        })
        this.bind(ls, early1)
        secondLifecycle.bind(ls, early2)
        return ls as Lifecycle<T, CT>
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle, [secondLifecycle], and [thirdLifecycle].
     *
     * This method creates a new lifecycle that depends on this lifecycle, [secondLifecycle], and [thirdLifecycle].
     * The child lifecycle will start when all three parent lifecycles have started, and will stop when
     * any of the parent lifecycles stops.
     *
     * The [start] function receives the data from all three parent lifecycles and should return the data
     * that the child lifecycle will manage. It also receives an [AutoClose] instance to register resources
     * that should be closed when the lifecycle ends.
     *
     * The [early1], [early2], and [early3] parameters control the execution order relative to other children
     * of the respective parent lifecycles.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any, P2 : Any, P3 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        thirdLifecycle: Lifecycle<*, P3>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        early1: Boolean = false,
        early2: Boolean = false,
        early3: Boolean = false,
        start: AutoClose.(T, P2, P3) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            val parentC = parents[2] as P3
            start(parentA, parentB, parentC)
        })
        this.bind(ls, early1)
        secondLifecycle.bind(ls, early2)
        thirdLifecycle.bind(ls, early3)
        return ls as Lifecycle<T, CT>
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle and three other lifecycles.
     *
     * This method creates a new lifecycle that depends on this lifecycle, [secondLifecycle],
     * [thirdLifecycle], and [fourthLifecycle]. The child lifecycle will start when all four parent
     * lifecycles have started, and will stop when any of the parent lifecycles stops.
     *
     * The [start] function receives the data from all four parent lifecycles and should return the data
     * that the child lifecycle will manage. It also receives an [AutoClose] instance to register resources
     * that should be closed when the lifecycle ends.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        thirdLifecycle: Lifecycle<*, P3>,
        fourthLifecycle: Lifecycle<*, P4>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        start: AutoClose.(T, P2, P3, P4) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            val parentC = parents[2] as P3
            val parentD = parents[3] as P4
            start(parentA, parentB, parentC, parentD)
        })
        this.bind(ls)
        secondLifecycle.bind(ls)
        thirdLifecycle.bind(ls)
        fourthLifecycle.bind(ls)
        return ls as Lifecycle<T, CT>
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle and four other lifecycles.
     *
     * This method creates a new lifecycle that depends on this lifecycle, [secondLifecycle],
     * [thirdLifecycle], [fourthLifecycle], and [fifthLifecycle]. The child lifecycle will start
     * when all five parent lifecycles have started, and will stop when any of the parent lifecycles stops.
     *
     * The [start] function receives the data from all five parent lifecycles and should return the data
     * that the child lifecycle will manage. It also receives an [AutoClose] instance to register resources
     * that should be closed when the lifecycle ends.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        thirdLifecycle: Lifecycle<*, P3>,
        fourthLifecycle: Lifecycle<*, P4>,
        fifthLifecycle: Lifecycle<*, P5>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        start: AutoClose.(T, P2, P3, P4, P5) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            val parentC = parents[2] as P3
            val parentD = parents[3] as P4
            val parentE = parents[4] as P5
            start(parentA, parentB, parentC, parentD, parentE)
        })
        this.bind(ls)
        secondLifecycle.bind(ls)
        thirdLifecycle.bind(ls)
        fourthLifecycle.bind(ls)
        fifthLifecycle.bind(ls)
        return ls as Lifecycle<T, CT>
    }

    /**
     * Creates and binds a new child lifecycle to this lifecycle and five other lifecycles.
     *
     * This method creates a new lifecycle that depends on this lifecycle, [secondLifecycle],
     * [thirdLifecycle], [fourthLifecycle], [fifthLifecycle], and [sixthLifecycle]. The child lifecycle
     * will start when all six parent lifecycles have started, and will stop when any of the parent
     * lifecycles stops.
     *
     * The [start] function receives the data from all six parent lifecycles and should return the data
     * that the child lifecycle will manage. It also receives an [AutoClose] instance to register resources
     * that should be closed when the lifecycle ends.
     *
     * @return The newly created and bound child lifecycle
     */
    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        thirdLifecycle: Lifecycle<*, P3>,
        fourthLifecycle: Lifecycle<*, P4>,
        fifthLifecycle: Lifecycle<*, P5>,
        sixthLifecycle: Lifecycle<*, P6>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        start: AutoClose.(T, P2, P3, P4, P5, P6) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            val parentC = parents[2] as P3
            val parentD = parents[3] as P4
            val parentE = parents[4] as P5
            val parentF = parents[5] as P6
            start(parentA, parentB, parentC, parentD, parentE, parentF)
        })
        this.bind(ls)
        secondLifecycle.bind(ls)
        thirdLifecycle.bind(ls)
        fourthLifecycle.bind(ls)
        fifthLifecycle.bind(ls)
        sixthLifecycle.bind(ls)
        return ls as Lifecycle<T, CT>
    }

    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, P7 : Any> bind(
        secondLifecycle: Lifecycle<*, P2>,
        thirdLifecycle: Lifecycle<*, P3>,
        fourthLifecycle: Lifecycle<*, P4>,
        fifthLifecycle: Lifecycle<*, P5>,
        sixthLifecycle: Lifecycle<*, P6>,
        seventhLifecycle: Lifecycle<*, P7>,
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        start: AutoClose.(T, P2, P3, P4, P5, P6, P7) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create<List<Any>, CT>(label, logLevel, stop, { parents ->
            val parentA = parents[0] as T
            val parentB = parents[1] as P2
            val parentC = parents[2] as P3
            val parentD = parents[3] as P4
            val parentE = parents[4] as P5
            val parentF = parents[5] as P6
            val parentG = parents[6] as P7
            start(parentA, parentB, parentC, parentD, parentE, parentF, parentG)
        })
        this.bind(ls)
        secondLifecycle.bind(ls)
        thirdLifecycle.bind(ls)
        fourthLifecycle.bind(ls)
        fifthLifecycle.bind(ls)
        sixthLifecycle.bind(ls)
        seventhLifecycle.bind(ls)
        return ls as Lifecycle<T, CT>
    }

    /**
     * Indicates whether this lifecycle has been started and initialized with a value.
     */
    val isInitialized get() = tree.value.selfState != null

    /**
     * Returns the current value of this lifecycle, throwing an error if the lifecycle hasn't been initialized.
     *
     * Use this property when you're certain the lifecycle has been started. If you're not sure,
     * use [value] which returns null for uninitialized lifecycles.
     *
     * @throws IllegalStateException if the lifecycle hasn't been initialized
     */
    override val assertValue: T get() = tree.value.selfState as T? ?: error("Attempt to get state of '${tree.value.label}' before it was initialized")

    /**
     * Returns the current value of this lifecycle, or null if the lifecycle hasn't been initialized.
     */
    override val value: T? get() = tree.value.selfState as T?

    /**
     * Copies the state of [lifecycles] into the children lifecycles of `this`.
     *
     * This method is useful when you have two lifecycle hierarchies with the same structure
     * and you want to transfer the state from one to the other. It copies the state from each
     * lifecycle in [lifecycles] to the corresponding child of this lifecycle.
     *
     * The [lifecycles] list must have the same size and structure as the children of this lifecycle.
     */
    fun copyChildrenStateFrom(lifecycles: List<Lifecycle<*, *>>) {
        //TODO: this would be more robust if it worked by label
        lifecycles.zip(tree.children).forEach { (source, dest) ->
            source.copyStateTo(dest)
        }
    }

    fun takeSnapshot(): LifecycleSnapshot {
        verifyUniqueLabels()
        val stateByLabel = mutableMapOf<String, LifecycleState>()
        tree.visit {
            stateByLabel[it.label] = LifecycleState(it.parentState, it.selfState)
        }
        return LifecycleSnapshot(stateByLabel)
    }

    private fun verifyUniqueLabels() {
        val labels = mutableSetOf<String>()
        val uniqueNodes = mutableSetOf<LifecycleTree>()
        tree.visitSubtrees {
            if (uniqueNodes.add(it)) {
                if (it.value.label in labels) {
                    throw IllegalStateException("Duplicate lifecycle label: ${it.value.label}. Keep your labels unique")
                }
                labels.add(it.value.label)
            }
        }
    }

    fun restoreFromSnapshot(snapshot: LifecycleSnapshot) {
        tree.visit {
            val savedValue = snapshot.stateByLabel[it.label] ?: return@visit
            it as LifecycleData<Any, Any>
            it.parentState = savedValue.parent
            it.selfState = savedValue.self
        }
    }

    fun close() {

    }

    /**
     * Copies over all the state of this lifecycle to [other], assuming [other] is of the exact same structure
     */
    private fun copyStateTo(other: LifecycleTree) {
        this.tree.visitTogetherWith(other) { thisNode, otherNode ->
            otherNode as LifecycleData<Any, Any>
            otherNode.selfState = thisNode.selfState
            otherNode.parentState = thisNode.parentState
        }
    }
}

internal class LifecycleState(
    val parent: Any?,
    val self: Any?,
)

class LifecycleSnapshot internal constructor(internal val stateByLabel: Map<String, LifecycleState>)

internal class LifecycleData<P : Any, T : Any>(
    var start: LifecycleContext<P, T>.(P) -> T,
    var stop: (T) -> Unit,
    var parentState: P?,
    var selfState: T?,
//    var throwOnFail: Boolean,
    var closed: Boolean,
    /**
     * Since lifecycles can have multiple parents, we need a way to differentiate between them.
     *
     * When a lifecycle is bound to a parent, that parent is given in its index in an internal list tracked by the lifecycle.
     * The parent then tracks its own index in the eyes of its child - [childrenParentIndices], to know how to identify itself to its child.
     *
     * For example, if you have one lifecycle with 3 children, and another lifecycle with the same 3 children, the first lifecycle will have
     * [childrenParentIndices] = `[0,0,0]`, and the second lifecycle will have [childrenParentIndices] = `[1,1,1]`.
     */
    val childrenParentIndices: MutableList<Int>,
    var parentCount: Int, // TO DO: when we unbind, have an assertion checking parentCount >=0
    // also when we unbind reorganize parentState because we could have created holes
    var logLevel: FunLogLevel,
    var label: String,
) {
    override fun toString(): String {
        return label
    }

    lateinit var lsCtx: LifecycleContext<P, T>
}

internal typealias LifecycleTree = MutableTree<LifecycleData<*, *>>

private fun <P : Any, T : Any> LifecycleData<P, T>.startSingle(
    parents: List<Pair<P, Int>>,
) {
    val hasMultipleParents = parentCount > 1

    val prevParent = parentState
    val prevSelf = selfState
    verifyState(prevParent, prevSelf, label)

    if (hasMultipleParents) {
        // Start initializing the parent data this lifecycle needs
        if (parentState == null) parentState = MutableList(parentCount) { null } as P
        // Special case that happens if the number of parents changes dynamically - we need to update the size of the list
        else if (parentState is List<*>) {
            val parent = parentState as List<*>
            if (parent.size != parentCount) parentState = MutableList(parentCount) { parent.getOrNull(it) } as P
        } else {
            val oldState = parentState
            // One parent becomes multiple parents
            parentState = MutableList(parentCount) { null } as P
            (parentState as MutableList<Any?>)[0] = oldState
        }
    }

    logLifecycleStart(this, hasMultipleParents, prevParent, label, prevSelf, parents)

    // Only modify data.parentState later to not mess with the logs
    for ((seedValue, index) in parents) {
        if (hasMultipleParents) {
            // If a reload changed it from 1 parents to 2, update it to be a list
            if (parentState !is MutableList<*>) parentState = mutableListOf(parentState) as P
            (parentState as MutableList<Any?>)[index] = seedValue
        } else {
            parentState = seedValue
        }
    }

    // Check for null parent state *before* the try-catch, so the error isn't swallowed.
    val nonNullParentState = parentState ?: error("startSingle should not have been run with a null seedValue when there is no preexisting parent state")

//    val time = measureTime {
    // Don't run if we don't have all the values yet
    if (nonNullParentState is List<*> && nonNullParentState.any { it == null }) return

    try {
        val result = lsCtx.start(nonNullParentState)
        closed = false
        selfState = result
    } catch (e: NoSuchMethodError) {
        //TODO: should think of a better thing to do when it fails, probably prevent children from running.
        log(FunLogLevel.Error) { "Failed to run lifecycle $label" }
        e.printStackTrace()
    }
//    }
//    log(logLevel) { "Ran $label in $time" }

}

private fun LifecycleData<*, *>.endSingle() {
    if (closed) {
        log(FunLogLevel.Warn) { "Attempt to close '$label' twice, ignoring" }
        return
    }
    val label = label
    val state = selfState
    if (state == null) {
        log(FunLogLevel.Warn) { "Failed to close '$label' as it was not successfully started" }
    } else {
        log(logLevel) {
            "Closing '$label' with '$state'"
        }
        (stop as (Any?) -> Unit)(state)
        if (state is AutoCloseable) {
            state.close()
        }
        lsCtx.close()
        closed = true
    }
}


private fun <P : Any, T : Any> verifyState(prevParent: P?, prevSelf: T?, label: String) {
    if (prevParent == null && prevSelf != null) {
        throw UnfunStateException("Lifecycle parent state of '$label' is null while its self state is not null. Since the parent state is always initialized before the self state, this should not be possible")
    }
}

private fun <P : Any, T : Any> logLifecycleStart(
    data: LifecycleData<P, T>,
    hasMultipleParents: Boolean,
    prevParent: P?,
    label: String,
    prevSelf: T?,
    parents: List<Pair<P, Int>>,
) {
    if (prevParent != null && prevSelf == null) {
        // With a single parent, we expect prevParent and prevSelf to be set both at once.
        log(FunLogLevel.Warn) {
            "Initializing '$label' with values $parents when a previous initialization with value '$prevParent' did not complete successfully."
        }
    } else {
        log(data.logLevel) {
            buildString {
                if (hasMultipleParents) {
                    when {
                        prevParent == null -> append("Initializing '$label' for the first time")
                        else -> {
                            append("Restarting '$label'")
                        }
                        // The last case is an error
                    }
                } else {
                    if (prevParent == null) append("Starting '$label' for the first time")
                    else append("Restarting '$label'")
                }
                if (parents.size == 1) append(" with parent = [${parents[0].first}]")
                else append(" with parents=${parents.sortedBy { it.second }.map { it.first }}")
                if (prevSelf != null) {
                    append(", replacing previous self values '$prevSelf'")
                }
                if (prevParent != null) {
                    append("and previous parent value '$prevParent'")
                }
            }
        }
    }
}


private fun <T> Tree<T>.visitTogetherWith(other: Tree<T>, callback: (T, T) -> Unit) {
    callback(this.value, other.value)
    children.zip(other.children).forEach { (thisChild, otherChild) ->
        thisChild.visitTogetherWith(otherChild, callback)
    }
}

fun main() {
    val window = Lifecycle.create<Float, Float>(
        label = "Window",
        start = { it },
        stop = { },
    )

    val dimensions = window.bind("Dimensions", start = { it }, stop = {})


    val wgpuSurface = window.bind("tesgpu Surface", start = { it }, stop = {})
    val wgpuDimensions = dimensions.bind(wgpuSurface, "testgpu Dimensions", start = { dim, surface -> dim + surface }, stop = {})
    val composeTexture = wgpuDimensions.bind("compotest Texture", start = { it }, stop = {})
    val composePipeline = wgpuSurface.bind("compotest Pipeline", start = { it }, stop = {})
    val composeBindGroup = composePipeline.bind(composeTexture, "compotest BindGroup", start = { pipeline, tex -> pipeline + tex }, stop = {})


    window.start(0.5f)

    window.end()

    wgpuSurface.restart()
}

/**
 * The current active log level. Messages with a level below this will not be logged.
 */
val activeLogLevel = FunLogLevel.Debug

/**
 * Logs a message if the specified [level] is greater than or equal to the [activeLogLevel].
 *
 * The [msg] lambda is only evaluated if the message will actually be logged, making this
 * function efficient for expensive message construction.
 */
inline fun log(level: FunLogLevel, msg: () -> String) {
    if (level.value >= activeLogLevel.value) {
        println(msg())
    }
}

/**
 * Log levels for the Fun engine, ordered from most verbose to least verbose.
 *
 * Each level has a numeric [value] that determines its priority. Higher values
 * indicate higher priority (less verbose) log levels.
 */
enum class FunLogLevel(val value: Int) {
    /**
     * Very detailed logs, useful for tracing execution flow
     */
    Verbose(0),

    /**
     * Detailed information useful for debugging
     */
    Debug(1),

    /**
     * General information about normal operation
     */
    Info(2),

    /**
     * Potential issues that don't prevent normal operation
     */
    Warn(3),

    /**
     * Serious issues that prevent normal operation
     */
    Error(4)
}
