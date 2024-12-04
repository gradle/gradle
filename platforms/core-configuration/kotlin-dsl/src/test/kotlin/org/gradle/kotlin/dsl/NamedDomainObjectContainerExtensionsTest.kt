package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.support.uncheckedCast
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class NamedDomainObjectContainerExtensionsTest {

    data class DomainObject(var foo: String? = null, var bar: Boolean? = null)

    @Test
    fun `can use monomorphic container api`() {

        val alice = DomainObject()
        val bob = DomainObject()
        val john = DomainObject()
        val marty = mockDomainObjectProviderFor(DomainObject())
        val doc = mockDomainObjectProviderFor(DomainObject())
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { getByName("alice") } doReturn alice
            on { create("bob") } doReturn bob
            on { maybeCreate("john") } doReturn john
            on { named("marty") } doReturn marty
            on { register("doc") } doReturn doc
            on { getByName(eq("alice"), any<Action<DomainObject>>()) } doReturn alice
            on { create(eq("bob"), any<Action<DomainObject>>()) } doReturn bob
        }

        // regular syntax
        container.getByName("alice") {
            foo = "alice-foo"
        }
        container.create("bob") {
            foo = "bob-foo"
        }
        container.maybeCreate("john")

        container.named("marty")
        container.register("doc")

        // invoke syntax
        container {
            getByName("alice") {
                foo = "alice-foo"
            }
            create("bob") {
                foo = "bob-foo"
            }
            maybeCreate("john")

            named("marty")
            register("doc")
        }
    }

    @Test
    fun `can use polymorphic container api`() {

        val alice = DomainObjectBase.Foo()
        val bob = DomainObjectBase.Bar()
        val default = DomainObjectBase.Default()
        val marty = DomainObjectBase.Foo()
        val martyProvider = mockDomainObjectProviderFor(marty)
        val doc = DomainObjectBase.Bar()
        val docProvider = mockDomainObjectProviderFor<DomainObjectBase>(doc)
        val docProviderAsBarProvider = uncheckedCast<NamedDomainObjectProvider<DomainObjectBase.Bar>>(docProvider)
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { getByName("alice") } doReturn alice
            on { maybeCreate("alice", DomainObjectBase.Foo::class.java) } doReturn alice
            on { create(eq("bob"), eq(DomainObjectBase.Bar::class.java), any<Action<DomainObjectBase.Bar>>()) } doReturn bob
            on { create("john") } doReturn default
            on { create("john", DomainObjectBase.Default::class.java) } doReturn default
            onNamedWithAction("marty", DomainObjectBase.Foo::class, martyProvider)
            on { register(eq("doc"), eq(DomainObjectBase.Bar::class.java)) } doReturn docProviderAsBarProvider
            onRegisterWithAction("doc", DomainObjectBase.Bar::class, docProviderAsBarProvider)
        }

        // regular syntax
        container.getByName<DomainObjectBase.Foo>("alice") {
            foo = "alice-foo-2"
        }
        container.maybeCreate<DomainObjectBase.Foo>("alice")
        container.create<DomainObjectBase.Bar>("bob") {
            bar = true
        }
        container.create("john")

        container.named("marty", DomainObjectBase.Foo::class) {
            foo = "marty-foo"
        }
        container.named<DomainObjectBase.Foo>("marty") {
            foo = "marty-foo-2"
        }
        container.register<DomainObjectBase.Bar>("doc") {
            bar = true
        }

        // invoke syntax
        container {
            getByName<DomainObjectBase.Foo>("alice") {
                foo = "alice-foo-2"
            }
            maybeCreate<DomainObjectBase.Foo>("alice")
            create<DomainObjectBase.Bar>("bob") {
                bar = true
            }
            create("john")

            named("marty", DomainObjectBase.Foo::class) {
                foo = "marty-foo"
            }
            named<DomainObjectBase.Foo>("marty") {
                foo = "marty-foo-2"
            }
            register<DomainObjectBase.Bar>("doc") {
                bar = true
            }
        }
    }

    @Test
    fun `can configure monomorphic container`() {

        val alice = DomainObject()
        val aliceProvider = mockDomainObjectProviderFor(alice)

        val bob = DomainObject()
        val bobProvider = mockDomainObjectProviderFor(bob)

        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { named("alice") } doReturn aliceProvider
            on { named("bob") } doReturn bobProvider
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
            equalTo(DomainObject("alice-foo", true))
        )

        assertThat(
            bob,
            equalTo(DomainObject("bob-foo", false))
        )
    }

    sealed class DomainObjectBase {
        data class Foo(var foo: String? = null) : DomainObjectBase()
        data class Bar(var bar: Boolean? = null) : DomainObjectBase()
        data class Default(val isDefault: Boolean = true) : DomainObjectBase()
    }

    @Test
    fun `can configure polymorphic container`() {

        val alice = DomainObjectBase.Foo()
        val aliceProvider = mockDomainObjectProviderFor(alice)

        val bob = DomainObjectBase.Bar()
        val bobProvider = mockDomainObjectProviderFor(bob)

        val default: DomainObjectBase = DomainObjectBase.Default()
        val defaultProvider = mockDomainObjectProviderFor(default)

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            onNamedWithAction("alice", DomainObjectBase.Foo::class, aliceProvider)
            on { named(eq("bob"), eq(DomainObjectBase.Bar::class.java)) } doReturn bobProvider
            on { named(eq("jim")) } doReturn defaultProvider
            on { named(eq("steve")) } doReturn defaultProvider
        }

        container {
            val a = "alice"(DomainObjectBase.Foo::class) {
                foo = "foo"
            }
            val b = "bob"(type = DomainObjectBase.Bar::class)
            val j = "jim" {}
            @Suppress("deprecation")
            val s = "steve"() // can invoke without a block, but must invoke

            assertThat(a.get(), sameInstance(alice))
            assertThat(b.get(), sameInstance(bob))
            assertThat(j.get(), sameInstance(default))
            assertThat(s.get(), sameInstance(default))
        }

        assertThat(
            alice,
            equalTo(DomainObjectBase.Foo("foo"))
        )

        assertThat(
            bob,
            equalTo(DomainObjectBase.Bar())
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `can create and configure tasks`() {

        val clean = mock<Delete>()
        val cleanProvider = mockTaskProviderFor(clean)

        val tasks = mock<TaskContainer> {
            on { create(eq("clean"), eq(Delete::class.java), any<Action<Delete>>()) } doReturn clean
            on { getByName("clean") } doReturn clean
            onNamedWithAction("clean", Delete::class, cleanProvider)
        }

        tasks {
            create<Delete>("clean") {
                delete("some")
            }
            getByName<Delete>("clean") {
                delete("stuff")
            }
            "clean"(type = Delete::class) {
                delete("things")
            }
        }

        tasks.getByName<Delete>("clean") {
            delete("build")
        }

        inOrder(clean) {
            verify(clean).delete("stuff")
            verify(clean).delete("things")
            verify(clean).delete("build")
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun `can create element within configuration block via delegated property`() {

        val tasks = mock<TaskContainer> {
            on { create("hello") } doReturn mock<Task>()
        }

        tasks {
            @Suppress("UnusedPrivateProperty")
            val hello by creating
        }
        verify(tasks).create("hello")
    }

    @Test
    fun `can get element of specific type within configuration block via delegated property`() {

        val task = mock<Exec>()
        val tasks = mock<TaskContainer> {
            on { getByName("hello") } doReturn task
        }

        @Suppress("UnusedPrivateProperty")
        tasks {
            val hello by getting(Exec::class)
        }
        verify(tasks).getByName("hello")
    }
}
