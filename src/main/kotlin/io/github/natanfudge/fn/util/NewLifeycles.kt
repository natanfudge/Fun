@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnfunStateException


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
@JvmInline
value class Lifecycle<P : Any, T : Any> private constructor(private val tree: LifecycleTree) {
    companion object {
        fun <P : Any, T : Any> create(start: (P) -> T, stop: (T) -> Unit, logLevel: FunLogLevel, label: String): Lifecycle<P, T> {
            return Lifecycle(
                MutableTreeImpl(
                    value = LifecycleData(
                        start = start,
                        stop = stop,
                        logLevel = logLevel,
                        label = label,
                        parentState = null,
                        parentCount = 0,
                        selfState = null,
                        childrenParentIndices = mutableListOf(),
                    ),
                    children = mutableListOf()
                )
            )
        }
    }

    /**
     * Starts this and all children recursively.
     * Passing null to [seedValue] would try to start the lifecycle with its existing data. It's recommended to use restart() for this purpose.
     */
    fun start(seedValue: P?, parentIndex: Int) {
        // The visitCounter tracks how many times each lifecycle node was visited.
        // For single-parent nodes, we can just run them immediately without tracking their visit counter.
        // For multi-parent nodes, initially, we will first increment their visit counter at the first visits, and when the final parent has visited
        // we will run them normally.
        // Afterwards, if all nodes have been visited and we have outstanding multi-parent nodes who have some parents that are never going to visit them,
        // we will try to rerun them anyway, using their existing data.
        val visitCounter = mutableMapOf<LifecycleTree, Int>()
        startRecur(seedValue, parentIndex, visitCounter)

        while (visitCounter.isNotEmpty()) {
            // Just take one out arbitrarily.
            // The idea now is to "squeeze out" the last remaining nodes that could possibly be rerun with partial new data.
            // As soon as another node is run, it could satisfy the requirements of other nodes, unblocking the entire tree traversal.
            val nextUnblockAttempt = visitCounter.keys.first()
            visitCounter.remove(nextUnblockAttempt)
            val wrapped = Lifecycle<Any, Any>(nextUnblockAttempt)
            // null seedValue as we are not adding any new seed data, just using the existing one
            // parentIndex is just a placeholder value, it will not be used
            wrapped.startRecur(seedValue = null, parentIndex = 0, visitCounter)
        }
    }

    /**
     * Ends this and all children recursively, bottom up, so children are closed before their parents.
     */
    fun end() {
        // note that this might not work for cases where a specific close order is desired.
        tree.visitBottomUp {
            it.endSingle()
        }
    }

    /**
     * Restarts this lifecycle
     */
    fun restart() {
        end()
        start(seedValue = null, parentIndex = 0) // Placeholder parentIndex, it won't be used
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
     */
    fun <CP : Any, CT : Any> bind(child: Lifecycle<CP, CT>) {
        val parentIndex = child.tree.value.parentCount
        child.tree.value.parentCount++
        tree.value.childrenParentIndices.add(parentIndex)
        tree.children.add(child.tree)
    }


    private fun startRecur(seedValue: P?, parentIndex: Int, visitCounter: MutableMap<LifecycleTree, Int>) {
        val data = tree.value as LifecycleData<P, T> // The Lifecycle<P,T> wrapper ensures us that the root is indeed LifecycleData<P, T>
        data.startSingle(seedValue, parentIndex, canRun = true)

        val selfState = data.selfState
            ?: error("A lifecycle with multiple parents cannot be started by itself without it having previously started by its parents.")
        check(data.childrenParentIndices.size == tree.children.size)
        data.childrenParentIndices.forEachIndexed { i, childParentIndex ->
            val child = tree.children[i]
            val wrappedChild = Lifecycle<T, Any>(child)
            val runChild = if (child.value.parentCount < 2) true
            else {
                val previousVisitCount = visitCounter.getOrElse(child) { 0 }
                // Side effect - update the amount of visits of the child
                visitCounter[child] = previousVisitCount + 1
                // The child can be ran if this is the last remaining parent
                val run = previousVisitCount == child.value.parentCount - 1
                // Side effect - stop tracking the child's visit counter. This also means that we won't try to re-run this child later.
                if (run) visitCounter.remove(child)
                run
            }
            if (runChild) {
                // Child ready to run - execute its code and keep following it recursively
                wrappedChild.start(selfState, childParentIndex)
            } else {
                val childValue = child.value as LifecycleData<T, Any>
                // Child not ready to run - just update its parent state
                childValue.startSingle(selfState, childParentIndex, canRun = false)
            }
        }
    }


}

private class LifecycleData<P : Any, T : Any>(
    val start: (P) -> T,
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
)

private typealias LifecycleTree = MutableTree<LifecycleData<*, *>>

private fun <P : Any, T : Any> LifecycleData<P, T>.startSingle(
    /**
     * [seedValue] can be null to signify no new seed value will be added, and the lifecycle should be rerun with the existing parentState.
     */
    seedValue: P?,
    parentIndex: Int,
    /**
     * In cases where a lifecycle has multiple parents, and has been initialized already,
     * we sometimes want to rerun it by supplying it with multiple values, but we only want it to actually
     * rerun when all parents that also need to rerun will give it their new data.
     * In that case, [startSingle] will be called multiple times, and only in the last time [canRun] will be true.
     *
     * [canRun] is assumed to be false no matter what if this lifecycle has multiple parents and some of them
     * have never provided their values before.
     *
     * [canRun] is assumed to be true no matter what if this lifecycle has 1 or 0 parents.
     *
     * [canRun] is assumed to be true no matter what if [seedValue] is null, as the only point of running this function would be to run the lifecycle.
     */
    canRun: Boolean,
) {
    verifyParentIndex(parentIndex, this, label)
    val hasMultipleParents = parentCount > 1

    val prevParent = parentState
    val prevSelf = selfState
    verifyState(prevParent, prevSelf)

    if (hasMultipleParents) {
        // Start initializing the parent data this lifecycle needs
        if (parentState == null) parentState = List(parentCount) { null } as P
    }

    val run = !hasMultipleParents || (canRun &&
            // Only allow rerunning if all the slots are filled (not null), with an exception for parentIndex which is going to get filled now.
            (parentState as List<Any?>).allIndexed { i, pState -> i == parentIndex || pState != null }
            )

    logLifecycleStart(this, hasMultipleParents, prevParent, label, prevSelf, run, parentIndex, seedValue)

    // Only modify data.parentState later to not mess with the logs
    if (hasMultipleParents && seedValue != null) {
        (parentState as MutableList<Any?>)[parentIndex] = seedValue
    }

    // TODO: remember to lock the start() and end() methods when a reload is occurring. It might be a good idea to also catch errors and try again for edge cases when reloading.

    parentState = seedValue
    if (run) {
        val result = start(
            seedValue ?: prevParent ?: error("startSingle should not have been run with a null seedValue when there is no preexisting parent state")
        )
        selfState = result
    }
}

private fun LifecycleData<*, *>.endSingle() {
    val label = label
    val state = selfState
    if (state == null) {
        log(FunLogLevel.Warn) { "Failed to close $label as it was not successfully started" }
    } else {
        log(logLevel) {
            "Closing $label with $state"
        }
        stop as (Any?) -> Unit
        stop(state)
        if (state is AutoCloseable) {
            state.close()
        }
    }
}




private fun <P : Any, T : Any> verifyState(prevParent: P?, prevSelf: T?) {
    if (prevParent == null && prevSelf != null) {
        throw UnfunStateException("Since the parent state is always initialized before the self state, this should not be possible")
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
    rerun: Boolean,
    parentIndex: Int,
    seedValue: P?,
) {
    if (!hasMultipleParents && prevParent != null && prevSelf == null) {
        // With a single parent, we expect prevParent and prevSelf to be set both at once.
        log(FunLogLevel.Warn) {
            "Initializing '$label' with value '$seedValue' when a previous initialization with value '$prevParent' did not complete successfully."
        }
    } else {
        log(data.logLevel) {
            buildString {
                if (hasMultipleParents) {
                    when {
                        prevParent == null -> append("Beginning initialization of '$label' for the first time")
                        prevSelf == null -> {
                            if (rerun) append("Finishing")
                            else append("Continuing")
                            append(" initialization of '$label' for the first time")
                        }

                        else -> {
                            if (rerun) append("Restarting '$label'")
                            else append("Supplying partial data for restart of '$label'")
                        }
                        // The last case is an error
                    }

                    append(" with parent=$parentIndex")


                } else {
                    if (prevParent == null) append("Starting '$label' for the first time")
                    else append("Restarting '$label'")
                }
                if (seedValue != null) {
                    append(" with seed value '$seedValue'")
                }
                if (prevSelf != null) append(", replacing previous self value '$prevSelf'")
                if (prevParent != null) {
                    if (prevSelf != null) append(" and")
                    else append(" replacing")
                    append(" previous parent value '$prevParent'")
                }
            }
        }
    }
}

//TODO: 1. try recreating the problematic parts of the Fun hierarchy
// 2. Tell ai to write some tests
// "generate tests for Lifecycle, exploring all possible options and failures. When asserting a failure, have it print out the actual failure when it occurs."

fun main() {
    val cycle = Lifecycle.create<Float, Float>(
        start = {
            println("Starting test root with value $it")
            it
        },
        stop = { println("Ending test root with value $it") },
        logLevel = FunLogLevel.Info,
        label = "Test Root"
    )


    cycle.start(0.5f, 0)

    cycle.end()

    cycle.start(0.8f, 0)

    cycle.restart()

}