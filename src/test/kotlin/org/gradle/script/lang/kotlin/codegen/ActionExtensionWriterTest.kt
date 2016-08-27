package org.gradle.script.lang.kotlin.codegen

import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer

import org.gradle.script.lang.kotlin.codegen.classNodeFor

import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import org.objectweb.asm.tree.ClassNode

import java.io.StringWriter

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class ActionExtensionWriterTest {

    @JvmField
    @Rule val projectDir = TemporaryFolder()

    @Test
    fun `will generate extension functions for methods with a last parameter of type Action`() {

        assertThat(
            extensionsFor(
                classNodeOf<Project>()),
            endsWith("""
package org.gradle.script.lang.kotlin

import org.gradle.api.Action

fun org.gradle.api.Project.delete(p0: org.gradle.api.file.DeleteSpec.() -> Unit): org.gradle.api.tasks.WorkResult =
	delete(Action { it.p0() })

fun org.gradle.api.Project.javaexec(p0: org.gradle.process.JavaExecSpec.() -> Unit): org.gradle.process.ExecResult =
	javaexec(Action { it.p0() })

fun org.gradle.api.Project.exec(p0: org.gradle.process.ExecSpec.() -> Unit): org.gradle.process.ExecResult =
	exec(Action { it.p0() })

fun org.gradle.api.Project.subprojects(p0: org.gradle.api.Project.() -> Unit): Unit =
	subprojects(Action { it.p0() })

fun org.gradle.api.Project.allprojects(p0: org.gradle.api.Project.() -> Unit): Unit =
	allprojects(Action { it.p0() })

fun org.gradle.api.Project.beforeEvaluate(p0: org.gradle.api.Project.() -> Unit): Unit =
	beforeEvaluate(Action { it.p0() })

fun org.gradle.api.Project.afterEvaluate(p0: org.gradle.api.Project.() -> Unit): Unit =
	afterEvaluate(Action { it.p0() })

fun <T : Any> org.gradle.api.Project.configure(p0: Iterable<T>, p1: T.() -> Unit): Iterable<T> =
	configure(p0, Action { it.p1() })

fun org.gradle.api.Project.copy(p0: org.gradle.api.file.CopySpec.() -> Unit): org.gradle.api.tasks.WorkResult =
	copy(Action { it.p0() })

fun org.gradle.api.Project.copySpec(p0: org.gradle.api.file.CopySpec.() -> Unit): org.gradle.api.file.CopySpec =
	copySpec(Action { it.p0() })

"""))
    }

    @Test
    fun `will fix non-generic Plugin type references`() {

        assertThat(
            extensionsFor(
                classNodeOf<PluginContainer>()),
            endsWith("""
fun org.gradle.api.plugins.PluginContainer.withId(p0: String, p1: org.gradle.api.Plugin<*>.() -> Unit): Unit =
	withId(p0, Action { it.p1() })

"""
            ))
    }

    @Test
    fun `will use parameter names from KDoc`() {

        val kDocProvider = MarkdownKDocProvider.from("""
# org.gradle.api.plugins.PluginContainer.withId(String, org.gradle.api.Plugin<*>.() -> Unit)

Lorem ipsum.

@param id plugin id.
@param configuration plugin configuration.
""")

        assertThat(
            extensionsFor(
                classNodeOf<PluginContainer>(),
                kDocProvider),
            endsWith("""
/**
 * Lorem ipsum.
 *
 * @param id plugin id.
 * @param configuration plugin configuration.
 */
fun org.gradle.api.plugins.PluginContainer.withId(id: String, configuration: org.gradle.api.Plugin<*>.() -> Unit): Unit =
	withId(id, Action { it.configuration() })

"""
            ))
    }

    private fun extensionsFor(classNode: ClassNode, kDocProvider: KDocProvider? = null): String {
        val output = StringWriter()
        ActionExtensionWriter(output, kDocProvider).writeExtensionsFor(classNode)
        return output.toString()
    }

    inline fun <reified T : Any> classNodeOf(): ClassNode =
        T::class.inputStream().use(::classNodeFor)

    fun <T : Any> KClass<T>.inputStream() =
        java.getResourceAsStream("/${jvmName.replace('.', '/')}.class")!!
}
