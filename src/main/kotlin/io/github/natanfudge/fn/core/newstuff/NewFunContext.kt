package io.github.natanfudge.fn.core.newstuff

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
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.TimeSource

// 3. Setup webgpu world renderer
// 3.5 Cleanup NewGlfwWindow
// 4. refresh app on resize


class NewFunEvents : NewFun("FunEvents") {
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
}


private val maxFrameDelta = 300.milliseconds

class RootFun : NewFun(parent = null, id = "", keys = listOf(Unit))

class NewFunContext(val appCallback: () -> Unit) : FunStateContext {
    init {
        NewFunContextRegistry.setContext(this)
    }

    private val initializer = FunInitializer()
    override val stateManager = FunStateManager()

    val rootFun = RootFun()

    val events = NewFunEvents()

    val fsWatcher = FileSystemWatcher()

    val logger = NewFunLogger()


    internal fun register(fn: NewFun) {
        stateManager.register(fn.id, allowReregister = true)
        initializer.requestInitialization(fn)
    }

    internal fun unregister(fn: NewFun) {
        stateManager.unregister(fn.id)
        initializer.remove(fn.id)
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

        appCallback()
        // Nothing to close, but its fine, it will start everything without closing anything
        initializer.finishRefresh()

        loop()
    }


    private fun loop() {
        while (true) {
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
    }


    private fun reload(reload: Reload) {
        events.appClosed(Unit)
        initializer.prepareForRefresh(reload.definitions.map { it.definitionClass.kotlin })
        appCallback()
        initializer.finishRefresh()
    }
}


internal object NewFunContextRegistry {
    private lateinit var context: NewFunContext
    fun setContext(context: NewFunContext) {
        this.context = context
    }

    fun getContext() = context
}

class SideEffectFun<T : Any>(
    parent: NewFun,
    id: String,
    keys: List<Any?>,
    typeChecker: TypeChecker,
    val initFunc: () -> T,
) : NewFun(id, keys, parent = parent), ReadOnlyProperty<Any?, T> {
    var value: T? by memo("value", typeChecker) { null }
    override fun init() {
        this.value = initFunc()
    }

    override fun cleanup() {
        (value as? AutoCloseable)?.close()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: error("Attempt to get value of side effect '${id}' before it has been created")
    }
}

// PropertyDelegateProvider<Any, ClientFunValue<T>> = PropertyDelegateProvider { _, property ->
//    funValue(initialValue, property.name, editor, beforeChange, afterChange)
//}
inline fun <reified T : Any> NewFun.sideEffect(vararg keys: Any?, noinline init: () -> T): PropertyDelegateProvider<Any, SideEffectFun<T>> = PropertyDelegateProvider { _, property ->
    SideEffectFun(this, property.name, keys.toList(), {it is T},init)
}

class FunBaseApp(config: WindowConfig) : NewFun("FunBaseApp", Unit) {
    @Suppress("unused")
    val glfwInit by sideEffect(Unit) {
        GlfwWindowProvider.initialize()
    }


    val window = NewGlfwWindow(withOpenGL = false, showWindow = true, config)
    val webgpu = NewWebGPUSurface(window)
    val worldRenderer = NewWorldRenderer(webgpu)
}
//TODO: Full refresh causes crasharino:
// assertion `left == right` failed: Device[Id(0,1)] is no longer alive
//  left: 1
// right: 2
//note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace

fun main() {
    val context = NewFunContext {
        val base = FunBaseApp(WindowConfig(initialWindowWidth = 800))
        TestApp(base.worldRenderer)

    }

    context.start()


}

class TestApp(val world: NewWorldRenderer) : NewFun("TestApp") {
    var instance by memo<RenderInstance> { null }
    override fun init() {
        val model = Model(Mesh.HomogenousCube, "Test")
        val value = object : Boundable {
            override val boundingBox: AxisAlignedBoundingBox
                get() = AxisAlignedBoundingBox.UnitAABB
        }

        this.instance = world.spawn(
            id, world.getOrBindModel(model), value, Transform().toMatrix(), Tint()
        )
    }

    override fun cleanup() {
        world.remove(instance)
    }
}