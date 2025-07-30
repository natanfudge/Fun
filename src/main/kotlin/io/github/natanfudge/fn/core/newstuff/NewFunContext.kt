package io.github.natanfudge.fn.core.newstuff

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.natanfudge.fn.core.FunStateContext
import io.github.natanfudge.fn.core.FunStateManager
import io.github.natanfudge.fn.core.InputEvent
import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.window.WindowConfig
import korlibs.time.milliseconds
import org.jetbrains.compose.reload.agent.Reload
import org.jetbrains.compose.reload.agent.invokeAfterHotReload
import org.jetbrains.compose.reload.core.mapLeft
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource

// 3. Setup webgpu world renderer
// 3.5 Cleanup NewGlfwWindow
// 4. refresh app on resize


/**
 * Note: this class is mounted directly on the FunContext which means its not reconstructed, although we could just have made it a normal component
 * and persisted the state lists.
 */
class NewFunEvents : NewFun("FunEvents", Unit) {
    // see https://github.com/natanfudge/MineTheEarth/issues/115
    val beforeFrame by event<Duration>()
    val frame by event<Duration>()
    val afterFrame by event<Duration>()
    val beforePhysics by event<Duration>()
    val physics by event<Duration>()
    val afterPhysics by event<Duration>()
    val input by event<InputEvent>()
    val guiError by event<Throwable>()
    val appClosed by event<Unit>()
    val hotReload by event<Reload>()

    // TODO: route to compose
    val densityChange by event<Density>()

    val windowResized by event<IntSize>()
    val afterWindowResized by event<IntSize>()
}

//TODO: setup automated testing where we try to invalidate different subgroups of Funs and see if it crashes

// TODO: compose rendering

// TODo: crasharino when trying to edit shader:
//
//thread '<unnamed>' panicked at C:\Users\runneradmin\.cargo\git\checkouts\wgpu-045f9a3b3e40a5c0\8a38f5f\wgpu-core\src\storage.rs:130:9:
//assertion `left == right` failed: Device[Id(0,1)] is no longer alive
//left: 1
//right: 2
//note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace

private val maxFrameDelta = 300.milliseconds

//TODO: 1. Changing shader crasharino
// 2. Event detachment warnings.

class NewFunContext(val appCallback: () -> Unit) : FunStateContext {
    init {
        NewFunContextRegistry.setContext(this)
    }


    val cache = FunCache()
    override val stateManager = FunStateManager()

    val rootFun = RootFun()

    val events = NewFunEvents()

    val fsWatcher = FileSystemWatcher()

    val logger = NewFunLogger()


    internal fun register(fn: NewFun) {
        stateManager.register(fn.id, allowReregister = true)
//        if (fn is SideEffectFun<*>) {
//            // TODO: temporary bandaid to keep the SideEffectFun system, we want to make it so it works with a different system unrelated to Fun.
//            initializer.requestInitialization(fn)
//        }
    }

    internal fun unregister(fn: NewFun) {
        stateManager.unregister(fn.id)
//        if (fn is SideEffectFun<*>) {
//            //TOdo: see comment in register()
//            initializer.remove(fn.id)
//        }
    }


    private var previousFrameTime = TimeSource.Monotonic.markNow()

    private var pendingReload: Reload? = null


    fun start() {
        invokeAfterHotReload { _, result ->
            println("Hot Reloading app with classes: ${result.leftOrNull()?.definitions?.map { it.definitionClass.simpleName }}")
            result.mapLeft {
                // This runs on a different thread which will cause issues, so we store it and will run it on the main thread later
                pendingReload = it
            }
        }

        events.hotReload.listenUnscoped {
            reload(it)
        }

        events.input.listenUnscoped {
            if (it is InputEvent.WindowClosePressed) exitProcess(0)
        }

        events.afterWindowResized.listenUnscoped {
            frame()
        }

        appCallback()
        // Nothing to close, but its fine, it will start everything without closing anything
//        initializer.finishRefresh()

        loop()
    }

    private fun frame() {
        val elapsed = previousFrameTime.elapsedNow()
        previousFrameTime = TimeSource.Monotonic.markNow()
        val delta = elapsed.coerceAtMost(maxFrameDelta)

        fsWatcher.poll()
        events.beforePhysics(delta)
        events.physics(delta)
        events.afterPhysics(delta)

        events.beforeFrame(delta)
        events.frame(delta)
        events.afterFrame(delta)

        //TODO: it's likely this is too late, and we need to run this pending reload check more often, since e.g. a refresh
        // might happen after beforePhysics and then physics will have old state.
        // But tbh this is an uphill battle, it would be best to interrupt the main thread on reload and do what we need.
        if (pendingReload != null) {
            // Run reload on main thread
            events.hotReload(pendingReload!!)
            pendingReload = null
        }
    }


    private fun loop() {
        while (true) {
            frame()
        }
    }


    private fun reload(reload: Reload) {
        aggregateDirtyClasses(reload)
        events.appClosed(Unit)
        cache.prepareForRefresh(reload.definitions.map { it.definitionClass.kotlin })
        rootFun.close(unregisterFromParent = false, deleteState = false)
        rootFun.clearChildren()
        appCallback()
        cache.finishRefresh()
    }
}


private fun aggregateDirtyClasses(reload: Reload) {
    val classes = mutableSetOf<String>()
    for (scope in reload.dirty.dirtyScopes) {
        classes.add(scope.methodId.classId.value)
    }
    println(classes)
    val x = 2
}


internal object NewFunContextRegistry {
    private lateinit var context: NewFunContext
    fun setContext(context: NewFunContext) {
        this.context = context
    }

    fun getContext() = context
}

//TODO: cleanup!

// TODO: since this is the only way we are using init/cleanup, we could simplify it into simply a keyed memo system, separate from Fun.
// For funs, we will close them all always.
class SideEffectFun<T : Any>(
    parent: NewFun,
    id: String,
    keys: List<Any?>,
    typeChecker: TypeChecker,
    val type: KClass<T>,
    val initFunc: () -> T,
) : NewFun(id, keys, parent = parent), ReadOnlyProperty<Any?, T> {
    //TODO: this makes no sense
//    var index = 0
//    var invalid = false
    var value: T? by memo("value", typeChecker) { null }
    override fun init() {
//        this.index++
        this.value = initFunc()
    }

    override fun equals(other: Any?): Boolean {
        // TODO: looks weird af but hack for key checks, it will invalidate by this check for now: it is NewFun && it.id in invalidValues
        return true
//        return other is SideEffectFun<T> && other.index == this.index
    }

//    override fun hashCode(): Int {
//        return index
//    }

    override fun cleanup() {
        (value as? AutoCloseable)?.close()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: error("Attempt to get value of side effect '${id}' before it has been created")
    }
}

//inline fun <reified T : Any> NewFun.cached(vararg keys: Any?, noinline init: () -> T): PropertyDelegateProvider<Any, SideEffectFun<T>> =
//    PropertyDelegateProvider { _, property ->
//        SideEffectFun(this, property.name, keys.toList(), { it is T }, T::class, init)
//    }

class FunBaseApp(config: WindowConfig) : NewFun("FunBaseApp", Unit) {
    @Suppress("unused")
    val glfwInit by cached(InvalidationKey.None) {
        GlfwWindowProvider.initialize()
    }


    val windowHolder = NewGlfwWindowHolder(withOpenGL = false, showWindow = true, config)
    val webgpu = NewWebGPUSurfaceHolder(windowHolder)
    val worldRenderer = NewWorldRenderer(webgpu)
}

fun main() {
    val context = NewFunContext {
        val base = FunBaseApp(WindowConfig(initialWindowWidth = 800))
        TestApp(base.worldRenderer)

    }

    context.start()


}

class TestApp(val world: NewWorldRenderer) : NewFun("TestApp") {
    val instance by cached(world.surfaceBinding) {
        val model = Model(Mesh.HomogenousCube, "Test")
        val value = object : Boundable {
            override val boundingBox: AxisAlignedBoundingBox
                get() = AxisAlignedBoundingBox.UnitAABB
        }
        world.spawn(
            id, world.getOrBindModel(model), value, Transform().toMatrix(), Tint(color = Color.Red)
        )
    }
}