package de.jensklingenberg

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import java.io.File

class SuperClassArgumentTest {

    @Test
    fun `Superclass constructor argument is injected`() {
        val fooSource = SourceFile.kotlin(
            "main.kt", """
            package test
            import sample.Bar

            class TestFoo : Bar()
            
            fun getFooName(): String? {
                return TestFoo().name
            }
            """
        )

        val file = File("../lib/src/commonMain/kotlin/sample/Bar.kt")
        require(file.exists()) { file.absolutePath }

        val barSource = SourceFile.fromPath(
            file
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(fooSource, barSource)
            compilerPluginRegistrars = listOf(CommonComponentRegistrar())
            commandLineProcessors = listOf(ExampleCommandLineProcessor())
            inheritClassPath = true
        }
        val result = compilation.compile()

        println("Compilation messages:\n${result.messages}")

        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val kClazz = result.classLoader.loadClass("test.MainKt")
        val getNameMethod = kClazz.getDeclaredMethod("getFooName")
        val name = getNameMethod.invoke(null) as? String

        assertThat(name).isEqualTo("TestFoo")
    }
}
