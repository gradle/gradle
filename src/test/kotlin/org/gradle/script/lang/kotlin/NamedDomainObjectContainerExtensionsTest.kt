package org.gradle.script.lang.kotlin

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class NamedDomainObjectContainerExtensionsTest {

    data class DomainObject(var foo: String? = null, var bar: Boolean? = null)

    @Test
    fun `can configure monomorphic container`() {

        val alice = DomainObject()
        val bob = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { maybeCreate("alice") } doReturn alice
            on { maybeCreate("bob") } doReturn bob
        }

        container {
            "alice" {
                foo = "alice-foo"
            }
            "alice" {
                // will configure the same object as the previous block
                bar = true
            }
            "bob" {
                foo = "bob-foo"
                bar = false
            }
        }

        assertThat(
            alice,
            equalTo(DomainObject("alice-foo", true)))

        assertThat(
            bob,
            equalTo(DomainObject("bob-foo", false)))
    }

    sealed class DomainObjectBase {
        data class Foo(var foo: String? = null) : DomainObjectBase()
        data class Bar(var bar: Boolean? = null) : DomainObjectBase()
        data class Default(val isDefault: Boolean = true) : DomainObjectBase()
    }

    @Test
    fun `can configure polymorphic container`() {

        val alice = DomainObjectBase.Foo()
        val bob = DomainObjectBase.Bar()
        val default: DomainObjectBase = DomainObjectBase.Default()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { maybeCreate("alice", DomainObjectBase.Foo::class.java) } doReturn alice
            on { maybeCreate("bob", DomainObjectBase.Bar::class.java) } doReturn bob
            on { maybeCreate("jim") } doReturn default
            on { maybeCreate("steve") } doReturn default
        }

        container {
            val a = "alice"(DomainObjectBase.Foo::class) {
                foo = "foo"
            }
            val b = "bob"(type = DomainObjectBase.Bar::class)
            val j = "jim" {}
            val s = "steve"() // can invoke without a block, but must invoke

            assertThat(a, sameInstance(alice))
            assertThat(b, sameInstance(bob))
            assertThat(j, sameInstance(default))
            assertThat(s, sameInstance(default))
        }

        assertThat(
            alice,
            equalTo(DomainObjectBase.Foo("foo")))

        assertThat(
            bob,
            equalTo(DomainObjectBase.Bar()))
    }

    @Test
    fun `can configure tasks`() {

        val clean = mock<Delete>()
        val tasks = mock<TaskContainer> {
            on { maybeCreate("clean", Delete::class.java) } doReturn clean
        }

        tasks {
            "clean"(type = Delete::class) {
                delete("build")
            }
        }

        verify(clean).delete("build")
    }
}


