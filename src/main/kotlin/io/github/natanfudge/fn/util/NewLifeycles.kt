@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnfunStateException
import io.github.natanfudge.fn.webgpu.AutoClose
import io.github.natanfudge.fn.webgpu.AutoCloseImpl
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LifecycleContext<P: Any, T: Any>(val thisLifecycle: Lifecycle<P,T>) : AutoClose by AutoCloseImpl() {

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
//TODO: stop using ReadOnlyProperty it causes bugs
class Lifecycle<P : Any, T : Any> private constructor(internal val tree: LifecycleTree) : ReadOnlyProperty<Any?, T> {
    companion object {
        fun <P : Any, T : Any> create(
            label: String,
            logLevel: FunLogLevel = FunLogLevel.Debug,
            stop: (T) -> Unit = {},
            start: LifecycleContext<P,T>.(P) -> T,
        ): Lifecycle<P, T> {
            val data = LifecycleData(
                start = start,
                stop = stop,
                logLevel = logLevel,
                label = label,
                parentState = null,
                parentCount = 0,
                selfState = null,
                childrenParentIndices = mutableListOf(),
            )
            val ls = Lifecycle<P,T>(
                MutableTreeImpl(
                    value = data,
                    children = mutableListOf()
                )
            )
            data.lsCtx = LifecycleContext(ls)
            return ls
        }
    }

    fun removeChildren(): List<Lifecycle<T, *>> {
        // Copy list
        val children = tree.children.toList().map { Lifecycle<T, Any>(it) }
        tree.children.clear()
        tree.value.childrenParentIndices.clear()
        return children
    }


    /**
     * Starts this and all children recursively.
     *
     * If this lifecycle has multiple parents, [parentIndex] may be used to specify which parent has a new value for this lifecycle.
     * Passing null to [seedValue] would try to start the lifecycle with its existing data. It's recommended to use restart() for this purpose.
     */
    fun start(seedValue: P?, parentIndex: Int = 0) {
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

            //TODO: there should just be one startSingle(List) method that sets all of the values and runs always
//            for (parent in parents) {
//                // Start non-root nodes
//                val indexForChild = parent.value.childrenParentIndices[parent.childIndex]
//                val parentValue =
//                    parent.value.selfState ?: error("It appears that topological sort failed - parent state is not initialized when reaching child")
//                child.startSingle(seedValue = parentValue, parentIndex = indexForChild, canRun = false)
//            }
//            // Start root
//            if (parents.isEmpty()) child.startSingle(seedValue, parentIndex, canRun = false)
//            child.startSingle(null, parentIndex = -1, canRun = true)
//            if(parent == null) {
//                // Root node
//                child as LifecycleData<P, T>
//                child.startSingle(seedValue,parentIndex, canRun = true)
//            } else {
//                child as LifecycleData<Any,Any>
//
//            }
        }
//        // The visitCounter tracks how many times each lifecycle node was visited.
//        // For single-parent nodes, we can just run them immediately without tracking their visit counter.
//        // For multi-parent nodes, initially, we will first increment their visit counter at the first visits, and when the final parent has visited
//        // we will run them normally.
//        // Afterwards, if all nodes have been visited and we have outstanding multi-parent nodes who have some parents that are never going to visit them,
//        // we will try to rerun them anyway, using their existing data.
//        val visitCounter = mutableMapOf<LifecycleTree, Int>()
//        //TODo: what i did doesn't work because inserted first doesn't mean should run first, sometimes children are visited before their parents and
//        // are just not run.
//        startRecur(seedValue, parentIndex, visitCounter)
//
//        while (visitCounter.isNotEmpty()) {
//            // Just take one out arbitrarily.
//            // The idea now is to "squeeze out" the last remaining nodes that could possibly be rerun with partial new data.
//            // As soon as another node is run, it could satisfy the requirements of other nodes, unblocking the entire tree traversal.
//            val nextUnblockAttempt = visitCounter.keys.first()
//            visitCounter.remove(nextUnblockAttempt)
//            val wrapped = Lifecycle<Any, Any>(nextUnblockAttempt)
//            // null seedValue as we are not adding any new seed data, just using the existing one
//            // parentIndex is just a placeholder value, it will not be used
//            wrapped.startRecur(seedValue = null, parentIndex = 0, visitCounter)
//        }
    }

    /**
     * Ends this and all children recursively, bottom up, so children are closed before their parents.
     */
    fun end() {
        val sort = tree.topologicalSort().asReversed()
        log(tree.value.logLevel) {
            "Ending tree of ${tree.value.label} by order: ${sort.joinToString("→") { it.child.label }}"
        }
        sort.forEach {
            it.child.endSingle()
        }

//        // note that this might not work for cases where a specific close order is desired.
//        tree.visitBottomUpUnique {
//            it.endSingle()
//        }
    }




    /**
     * Restarts this lifecycle
     */
    fun restart() {
        end()
        start(seedValue = null, parentIndex = 0) // Placeholder parentIndex, it won't be used
    }

    fun restartByLabel(label: String) {
        tree.visitSubtrees {
            if (it.value.label == label) {
                Lifecycle<Any, Any>(it).restart()
                return
            }
        }
        println("Did not find any lifecycle with the label '$label' to restart")
    }

    /**
     * Restarts this lifecycle with a specific [seedValue]
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
     * The [child] will receive the data of this lifecycle when it is started.
     * If this is the only lifecycle [child] is bound to, then the data of this lifecycle will be passed directly (CP = T)
     * If [child] will be bound to more lifecycles, the child will receive a list of values, each having the value of each parent,
     * ordered by the order it was bound to its parents.
     *
     * @param runEarly If true, the child will be inserted before all other children, ensuring it runs first.
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
     * Calls [start] when `this` starts, and [stop] when this stops.
     * @param early If true, the child will be inserted before all other children, ensuring it runs first.
     */
    fun <CT : Any> bind(
        label: String,
        logLevel: FunLogLevel = FunLogLevel.Debug,
        stop: (CT) -> Unit = {},
        early: Boolean = false,
        start: LifecycleContext<T,CT>.(T) -> CT,
    ): Lifecycle<T, CT> {
        val ls = create(label, logLevel, stop, start)
        bind(ls, early)
        return ls
    }

    /**
     * Calls [start] when `this` and [secondLifecycle] start, and [stop] when this and [secondLifecycle] stop.
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
     * Calls [start] when `this`, [secondLifecycle], [thirdLifecycle] start, and [stop] when they stop.
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
     * Calls [start] when `this`, [secondLifecycle], [thirdLifecycle] start, and [stop] when they stop.
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
     * Calls [start] when `this`, [secondLifecycle], [thirdLifecycle] start, and [stop] when they stop.
     */
    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any, P5: Any> bind(
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
     * Calls [start] when `this`, [secondLifecycle], [thirdLifecycle] start, and [stop] when they stop.
     */
    fun <CT : Any, P2 : Any, P3 : Any, P4 : Any, P5: Any, P6: Any> bind(
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

    val isInitialized get() = tree.value.selfState != null
    val assertValue: T get() = tree.value.selfState as T? ?: error("Attempt to get state of '${tree.value.label}' before it was initialized")
    val value: T? get() = tree.value.selfState as T?

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return tree.value.selfState as T? ?: error("Attempt to get state of '${tree.value.label}' before it was initialized")
    }

    /**
     * Copies the state of [lifecycles] into the children lifecycles of `this`.
     */
    fun copyChildrenStateFrom(lifecycles: List<Lifecycle<*, *>>) {
        lifecycles.zip(tree.children).forEach { (source, dest) ->
            source.copyStateTo(dest)
        }
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
//
//    private fun startRecur(seedValue: P?, parentIndex: Int, visitCounter: MutableMap<LifecycleTree, Int>) {
//        val data = tree.value as LifecycleData<P, T> // The Lifecycle<P,T> wrapper ensures us that the root is indeed LifecycleData<P, T>
//        // Attempt to start/update the current lifecycle node.
//        // The canRun=true here is because startRecur is either:
//        // 1. The top-level call from Lifecycle.start() (which implies it should try to run).
//        // 2. A recursive call for a child that was deemed runnable by its parent.
//        // 3. A call from the "squeeze-out" loop, which is also a "try to run" scenario.
//        data.startSingle(seedValue, parentIndex, canRun = true)
//
//        val selfState = data.selfState
//        if (selfState == null) {
//            // If, after attempting to start/update, selfState is still null,
//            // it means this lifecycle isn't fully ready (e.g., a multi-parent lifecycle still waiting for other parents,
//            // or its start lambda couldn't produce a value perhaps due to its own internal logic or incomplete parent data).
//            // In this state, it cannot provide a valid state to its children, so we should not proceed to start them.
//            // The visitCounter mechanism (if this node is part of it) should ensure it's re-evaluated if other relevant parent lifecycles start or update.
//            return
//        }
//
//        // If we've reached here, selfState is non-null, meaning the current lifecycle has successfully started/updated.
//        // Now, proceed to process its children.
//
//        check(data.childrenParentIndices.size == tree.children.size) {
//            "Mismatch between tracked children parent indices (${data.childrenParentIndices.size}) and actual children count (${tree.children.size}) for lifecycle '${data.label}'"
//        }
//        data.childrenParentIndices.forEachIndexed { i, childParentIndex ->
//            val childTree = tree.children[i]
//            // The child lifecycle expects the current lifecycle's output (selfState of type T) as its input (P type for the child).
//            val wrappedChild = Lifecycle<T, Any>(childTree) // Child's P type is T (selfState's type), CT is Any for generality here.
//
//            val runChildNow = if (childTree.value.parentCount < 2) {
//                true // Single-parent children always try to run if their parent runs.
//            } else {
//                // Multi-parent child logic:
//                val previousVisitCount = visitCounter.getOrElse(childTree) { 0 }
//                // Side effect - update the amount of visits of the child
//                visitCounter[childTree] = previousVisitCount + 1
//                // The child can be ran if this is the last remaining parent
//                val run = previousVisitCount == childTree.value.parentCount - 1
//                // Side effect - stop tracking the child's visit counter. This also means that we won't try to re-run this child later.
//                if (run) visitCounter.remove(childTree)
//                run
//            }
//            if (runChildNow) {
//                // This child is ready to be fully started/restarted.
//                wrappedChild.startRecur(selfState, childParentIndex, visitCounter)
//            } else {
//                // This multi-parent child is not yet ready for a full run (waiting for other parents).
//                // Just provide it with the current parent's state.
//                val childLifecycleData = childTree.value as LifecycleData<T, Any>
//                childLifecycleData.startSingle(selfState, childParentIndex, canRun = false)
//            }
//        }
//    }
//
//
}

internal class LifecycleData<P : Any, T : Any>(
    val start: LifecycleContext<P,T>.(P) -> T,
    val stop: (T) -> Unit,
    var parentState: P?,
    var selfState: T?,
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
    var parentCount: Int, //TODO: when we unbind, have an assertion checking parentCount >=0
    // TODO: also when we unbind reorganize parentState because we could have created holes
    val logLevel: FunLogLevel,
    val label: String,
) {
    override fun toString(): String {
        return label
    }

    lateinit var lsCtx: LifecycleContext<P,T>
}

internal typealias LifecycleTree = MutableTree<LifecycleData<*, *>>

private fun <P : Any, T : Any> LifecycleData<P, T>.startSingle(
    parents: List<Pair<P, Int>>,
//    /**
//     * [seedValue] can be null to signify no new seed value will be added, and the lifecycle should be rerun with the existing parentState.
//     */
//    seedValue: P?,
//    parentIndex: Int,
//    /**
//     * In cases where a lifecycle has multiple parents, and has been initialized already,
//     * we sometimes want to rerun it by supplying it with multiple values, but we only want it to actually
//     * rerun when all parents that also need to rerun will give it their new data.
//     * In that case, [startSingle] will be called multiple times, and only in the last time [canRun] will be true.
//     *
//     * [canRun] is assumed to be false no matter what if this lifecycle has multiple parents and some of them
//     * have never provided their values before.
//     *
//     * [canRun] is assumed to be true no matter what if this lifecycle has 1 or 0 parents.
//     *
//     * [canRun] is assumed to be true no matter what if [seedValue] is null, as the only point of running this function would be to run the lifecycle.
//     */
//    canRun: Boolean,
) {
//    verifyParentIndex(parentIndex, this, label)
    val hasMultipleParents = parentCount > 1

    val prevParent = parentState
    val prevSelf = selfState
    verifyState(prevParent, prevSelf, label)

    if (hasMultipleParents) {
        // Start initializing the parent data this lifecycle needs
        if (parentState == null) parentState = List(parentCount) { null } as P
    }

    logLifecycleStart(this,hasMultipleParents,prevParent,label,prevSelf, parents)

//    val run = canRun &&
//            (if (!hasMultipleParents) true else
//            // Only allow rerunning if all the slots are filled (not null), with an exception for parentIndex which is going to get filled now.
//            (parentState as List<Any?>).allIndexed { i, pState -> i == parentIndex || pState != null })

//    logLifecycleStart(this, hasMultipleParents, prevParent, label, prevSelf, run, parentIndex, seedValue)

    // Only modify data.parentState later to not mess with the logs
    for ((seedValue, index) in parents) {
        if (hasMultipleParents) {
            (parentState as MutableList<Any?>)[index] = seedValue
        } else {
            parentState = seedValue
        }
    }

//    if (seedValue != null) {
//
//    }

    // TODO: remember to lock the start() and end() methods when a reload is occurring. It might be a good idea to also catch errors and try again for edge cases when reloading.


//    if (run) {
    // Check for null parent state *before* the try-catch, so the error isn't swallowed.
    val nonNullParentState = parentState ?: error("startSingle should not have been run with a null seedValue when there is no preexisting parent state")

    try {
        val result = lsCtx.start(nonNullParentState)
        selfState = result
    } catch (e: Throwable) {
        //TODO: should think of a better thing to do when it fails, probably prevent children from running.
        log(FunLogLevel.Error) { "Failed to run lifecycle $label" }
        e.printStackTrace()
        // Optionally rethrow specific critical errors if needed, but for now, just log.
    }
//    }
}

private fun LifecycleData<*, *>.endSingle() {
    val label = label
    val state = selfState
    if (state == null) {
        log(FunLogLevel.Warn) { "Failed to close '$label' as it was not successfully started" }
    } else {
        log(logLevel) {
            "Closing '$label' with '$state'"
        }
        stop as (Any?) -> Unit
        stop(state)
        if (state is AutoCloseable) {
            state.close()
        }
        lsCtx.close()
    }
}


private fun <P : Any, T : Any> verifyState(prevParent: P?, prevSelf: T?, label: String) {
    if (prevParent == null && prevSelf != null) {
        throw UnfunStateException("Lifecycle parent state of '$label' is null while its self state is not null. Since the parent state is always initialized before the self state, this should not be possible")
    }
}

private fun <P : Any, T : Any> verifyParentIndex(parentIndex: Int, data: LifecycleData<P, T>, label: String) {
    require(parentIndex == 0 || parentIndex < data.parentCount) {
        val problem = when (data.parentCount) {
            0 -> "has no parents"
            1 -> "has only one parent"
            else -> "has only ${data.parentCount} parents"
        }
        "Lifecycle '$label' was initialized with a value belonging to parent index $parentIndex, but the lifecycle $problem"
    }
}

private fun <P : Any, T : Any> logLifecycleStart(
    data: LifecycleData<P, T>,
    hasMultipleParents: Boolean,
    prevParent: P?,
    label: String,
    prevSelf: T?,
    parents: List<Pair<P, Int>>
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
                if(parents.size ==1) append(" with parent = [${parents[0].first}]")
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