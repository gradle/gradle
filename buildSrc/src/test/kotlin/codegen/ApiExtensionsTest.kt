package codegen

import groovy.lang.Closure
import org.gradle.api.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiExtensionsTest {

    @Test
    fun `#extensionFunctionsOf should find methods with a last parameter of type Action{T} skipping generic functions`() {
        assertEquals(
            listOf(IProject::withAction),
            extensibleFunctionsOf(IProject::class).map { it.definition })
    }

    @Test
    fun `#extensionCodeFor should generate correct delegation code`() {
        assertEquals(
            "inline fun IProject.withAction(s: String, crossinline action: codegen.IProject.() -> Unit) =\n" +
                "    this.withAction(s, org.gradle.api.Action { action(it) })",
            extensionCodeFor(ExtensibleFunction(IProject::withAction)))
    }
}

@Suppress("unused")
interface IProject {
    fun withAction(s: String, action: Action<in IProject>): Unit
    fun withClosure(closure: Closure<*>): Unit
    fun <T> generic(element: T, action: Action<T>): Unit // Not supported yet
}
