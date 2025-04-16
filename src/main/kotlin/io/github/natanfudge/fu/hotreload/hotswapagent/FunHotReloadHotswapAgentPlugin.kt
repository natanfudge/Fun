package io.github.natanfudge.fu.hotreload.hotswapagent

import io.github.natanfudge.fu.hotreload.FunHotReload.observation
import io.github.natanfudge.fu.hotreload.hotswapagent.FunHotReloadHotswapAgentPlugin.Companion.LOGGER
import org.hotswap.agent.annotation.Init
import org.hotswap.agent.annotation.LoadEvent
import org.hotswap.agent.annotation.OnClassLoadEvent
import org.hotswap.agent.annotation.Plugin
import org.hotswap.agent.command.MergeableCommand
import org.hotswap.agent.command.Scheduler
import org.hotswap.agent.javassist.CtClass
import org.hotswap.agent.logging.AgentLogger
import org.hotswap.agent.util.PluginManagerInvoker


/**
 * The plugin system annotation is similar to Spring MVC way - use method annotation with variable
 * method attributes types. See each annotation javadoc for available attribute types and usage.
 *
 *
 * Always be aware of which classloader your code use (Application or agent classloader?) More on
 * classloader issues in
 * [Agent documentation](https://github.com/HotswapProjects/HotswapAgent/blob/master/HotswapAgent/README.md)
 */
@Plugin(
    name = "FunHotReloadHotswapAgentPlugin",
    description = "Hotswap agent plugin as part of normal application.",
    testedVersions = ["0.0.1"],
    expectedVersions = ["0.0.1"]
)
class FunHotReloadHotswapAgentPlugin {
    /**
     * All compiled code in ExamplePlugin is executed in agent classloader and cannot access
     * framework/application classes. If you need to call a method on framework class, use application
     * classloader. It is injected on plugin initialization.
     */
    @field:Init
    lateinit var appClassLoader: ClassLoader


    // Scheduler service - use to run a command asynchronously and merge multiple similar commands to one execution
    // static  - Scheduler and other agent services are available even in static context (before the plugin is initialized)
    @field:Init
    lateinit var scheduler: Scheduler


    /**
     * Count how many classes were reloaded via hotswap after the plugin is initialized.
     *
     * (Note - if you test the behaviour and reload TestEntityService or TestRepository - the spring bean
     * gets reloaded and new instance is created, try the behaviour).
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = [LoadEvent.REDEFINE])
    fun reloadClass(className: String?) {
        LOGGER.info("Detected class reload: {}", className)
        // run the logic in a command. Multiple reload commands may be merged into one execution (see the command class)
        scheduler.scheduleCommand(ReloadClassCommand(appClassLoader, className))
    }

    companion object {

        // Agent logger is a very simple custom logging mechanism. Do not use any common logging framework
        // to avoid compatibility and classloading issues.
        val LOGGER: AgentLogger = AgentLogger.getLogger(FunHotReloadHotswapAgentPlugin::class.java)


        /**
         * Any plugin has to have at least one static @OnClassLoadEvent method to hook initialization code. It is usually
         * some key framework method. Call PluginManager.initializePlugin() to create new plugin instance and
         * initialize agentexamples with the application classloader. Than call one or more methods on the plugin
         * to pass reference to framework/application objects.
         *
         * @param ctClass see @OnClassLoadEvent javadoc for available parameter types. CtClass is convenient way
         * to enhance method bytecode using javaasist
         */
        @OnClassLoadEvent(classNameRegexp = "io.github.natanfudge.fu.hotreload.FunHotReload")
        @JvmStatic
        fun transformTestEntityService(ctClass: CtClass) {
            // You need always find a place from which to initialize the plugin.
            // Initialization will create new plugin instance (notice that transformTestEntityService is
            // a static method), inject agent services (@Inject) and register event listeners (@OnClassLoadEvent and @OnResourceFileEvent).
            val src = PluginManagerInvoker.buildInitializePlugin(FunHotReloadHotswapAgentPlugin::class.java)

            //  enhance default constructor using javaasist. Plugin manager (TransformHandler) will use enhanced class
            // to replace actual bytecode.
            ctClass.getDeclaredConstructor(arrayOfNulls<CtClass>(0)).insertAfter(src)
        }
    }
}


/**
 * /**
 *  * @param appClassLoader Usually you need the application classloader as a parameter - to know in which
 *  * classloader the class you want to call lives
 *  * @param className      reloaded className
 *  */
 * A command to merge multiple reload events into one execution and execute the logic in application classloader.
 */
class ReloadClassCommand(var appClassLoader: ClassLoader, var className: String?) : MergeableCommand() {

    override fun executeCommand() {
        try {
            // Now we have application classloader and the service on which to invoke the method, we can use
            // reflection directly
            // but for demonstration purpose we invoke a plugin class, that lives in the application classloader
            val setExamplePluginResourceText = appClassLoader.loadClass(ReloadClassService::class.java.getName())
                .getDeclaredMethod("classReloaded", String::class.java)
            setExamplePluginResourceText.invoke(null, className)
        } catch (e: Exception) {
            LOGGER.error("Error invoking {}.reload()", e, ReloadClassService::class.java.getName())
        }
    }

    /**
     * Use equals to group "similar commands". If multiple "equals" commands are scheduled during
     * the scheduler timeout, only the last command is executed. If you need information regarding
     * all merged commands and/or select which is executed, use MergeableCommand superclass.
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        return o != null && javaClass == o.javaClass
    }

    override fun hashCode(): Int {
        // Discard all copies except for one
        return 0
    }
}

/**
 * This class should be used in the application classloader.
 *
 * * Hotswap agent defines plugin classes in BOTH classloaders agent AND application. You need to know for which
 * classloader is each class targeted. In this example plugin, this is the only class targeted towards application
 * classloader.
 */
object ReloadClassService {
    /**
     * Method invoked from ReloadClassCommand using reflection in application classloader.
     *
     * @param className        class name
     */
    @JvmStatic
    fun classReloaded(className: String?) {
        observation.emit(Unit)
    }
}