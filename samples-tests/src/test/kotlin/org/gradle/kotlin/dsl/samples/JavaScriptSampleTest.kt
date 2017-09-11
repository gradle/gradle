package org.gradle.kotlin.dsl.samples


import org.junit.Test
import java.io.File
import java.io.FileReader
import javax.script.ScriptEngineManager
import java.io.StringWriter
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.equalTo

class JavaScriptSampleTest : AbstractSampleTest("hello-js") {

    @Test
    fun `hello world`() {
        build("assemble")

        val sw = StringWriter()
        val engine = ScriptEngineManager().getEngineByName("nashorn").apply {
            // Redirect output from `print` to this writer
            context.writer = sw
        }

        // Wire `console.log` to `print`
        engine.eval("var console = {}; console.log = print;")

        // Load Kotlin JS stdlib
        engine.eval(FileReader(File("$projectRoot/build/web/kotlin.js")))
        // Run build output
        engine.eval(FileReader(File("$projectRoot/build/web/output.js")))

        assertThat(
            sw.buffer.toString(),
            equalTo("Hello, world!\n"))
    }
}
