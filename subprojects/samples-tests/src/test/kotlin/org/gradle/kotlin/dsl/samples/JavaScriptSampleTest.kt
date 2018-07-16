package org.gradle.kotlin.dsl.samples

import org.junit.Test
import java.io.File
import java.io.FileReader
import javax.script.ScriptEngineManager
import java.io.StringWriter
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.equalTo
import java.io.Writer
import javax.script.ScriptEngine


class JavaScriptSampleTest : AbstractSampleTest("hello-js") {

    @Test
    fun `hello world`() {

        build("assemble")

        val javaScriptOutput = StringWriter()
        val engine = javaScriptEngine(javaScriptOutput)

        // Load Kotlin JS stdlib
        engine.eval(existing("build/web/kotlin.js"))

        // Run build output
        engine.eval(existing("build/web/output.js"))

        assertThat(
            javaScriptOutput.toString().trim(),
            equalTo("Hello, world!"))
    }


    private
    fun javaScriptEngine(outputWriter: Writer) =
        ScriptEngineManager().getEngineByName("nashorn").apply {

            // Redirect output from `print` to this writer
            context.writer = outputWriter

            // Wire `console.log` to `print`
            eval("var console = {}; console.log = print;")
        }


    private
    fun ScriptEngine.eval(file: File) =
        eval(FileReader(file))
}
