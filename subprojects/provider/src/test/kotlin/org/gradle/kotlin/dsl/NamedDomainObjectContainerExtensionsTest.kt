package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.*

import org.gradle.api.DomainObjectProvider
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

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
        }

        // regular syntax
        container.getByName("alice") {
            it.foo = "alice-foo"
        }
        container.create("bob") {
            it.foo = "bob-foo"
        }
        container.maybeCreate("john")

        container.named("marty")
        container.register("doc")

        // invoke syntax
        container {
            getByName("alice") {
                it.foo = "alice-foo"
            }
            create("bob") {
                it.foo = "bob-foo"
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
        val martyProvider = mockDomainObjectProviderFor<DomainObjectBase>(marty)
        val doc = DomainObjectBase.Bar()
        val docProvider = mockDomainObjectProviderFor<DomainObjectBase>(doc)
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { getByName("alice") } doReturn alice
            on { maybeCreate("alice", DomainObjectBase.Foo::class.java) } doReturn alice
            on { create(argThat { equals("bob") }, argThat { equals(DomainObjectBase.Bar::class.java) }, any<Action<DomainObjectBase.Bar>>()) } doReturn bob
            on { create("john", DomainObjectBase.Default::class.java) } doReturn default
            on { named("marty") } doReturn martyProvider
            on { register(argThat { equals("doc") }, argThat { equals(DomainObjectBase.Bar::class.java) }) } doReturn docProvider
            on { register(argThat { equals("doc") }, argThat { equals(DomainObjectBase.Bar::class.java) }, any<Action<DomainObjectBase.Default>>()) }.thenAnswer {
                it.getArgument<Action<DomainObjectBase>>(2).execute(doc)
                docProvider
            }
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
        val aliceProvider = mock<DomainObjectProvider<DomainObject>> {
            on { get() } doReturn alice
            on { configure(any()) } doAnswer {
                it.getArgument<Action<DomainObject>>(0).execute(alice)
            }
        }

        val bob = DomainObject()
        val bobProvider = mock<DomainObjectProvider<DomainObject>> {
            on { get() } doReturn bob
            on { configure(any()) } doAnswer {
                it.getArgument<Action<DomainObject>>(0).execute(bob)
            }
        }

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
        val aliceProvider = mock<DomainObjectProvider<DomainObjectBase.Foo>> {
            on { get() } doReturn alice
            on { configure(any()) } doAnswer {
                it.getArgument<Action<DomainObjectBase.Foo>>(0).execute(alice)
            }
        }

        val bob = DomainObjectBase.Bar()
        val bobProvider = mock<DomainObjectProvider<DomainObjectBase.Bar>> {
            on { get() } doReturn bob
            on { configure(any()) } doAnswer {
                it.getArgument<Action<DomainObjectBase.Bar>>(0).execute(bob)
            }
        }

        val default: DomainObjectBase = DomainObjectBase.Default()
        val defaultProvider = mock<DomainObjectProvider<DomainObjectBase>> {
            on { get() } doReturn default
            on { configure(any()) } doAnswer {
                it.getArgument<Action<DomainObjectBase>>(0).execute(default)
            }
        }

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { named("alice") } doReturn uncheckedCast<DomainObjectProvider<DomainObjectBase>>(aliceProvider)
            on { named("bob") } doReturn uncheckedCast<DomainObjectProvider<DomainObjectBase>>(bobProvider)
            on { named("jim") } doReturn defaultProvider
            on { named("steve") } doReturn defaultProvider
        }

        container {
            val a = "alice"(DomainObjectBase.Foo::class) {
                foo = "foo"
            }
            val b = "bob"(type = DomainObjectBase.Bar::class)
            val j = "jim" {}
            val s = "steve"() // can invoke without a block, but must invoke

            assertThat(a.get(), sameInstance(alice))
            assertThat(b.get(), sameInstance(bob))
            assertThat(j.get(), sameInstance(default))
            assertThat(s.get(), sameInstance(default))
        }

        assertThat(
            alice,
            equalTo(DomainObjectBase.Foo("foo")))

        assertThat(
            bob,
            equalTo(DomainObjectBase.Bar()))
    }

    @Test
    fun `can create and configure tasks`() {

        val clean = mock<Delete>()
        val cleanProvider = mock<TaskProvider<Delete>> {
            on { get() } doReturn clean
            on { configure(any()) } doAnswer {
                it.getArgument<Action<Delete>>(0).execute(clean)
            }
        }

        val tasks = mock<TaskContainer> {
            on { create(argThat { equals("clean") }, argThat { equals(Delete::class.java) }, any<Action<Delete>>()) } doReturn clean
            on { getByName("clean") } doReturn clean
            on { named("clean") } doReturn uncheckedCast<TaskProvider<Task>>(cleanProvider)
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

    @Test
    fun `can create element in monomorphic container via delegated property`() {

        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("domainObject") } doReturn DomainObject()
        }

        @Suppress("unused_variable")
        val domainObject by container.creating

        verify(container).create("domainObject")
    }

    @Test
    fun `can create and configure element in monomorphic container via delegated property`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("domainObject") } doReturn element
            on { getByName("domainObject") } doReturn element
        }

        val domainObject by container.creating {
            foo = "domain-foo"
            bar = true
        }

        verify(container).create("domainObject")
        assertThat(
            domainObject,
            equalTo(DomainObject("domain-foo", true)))
    }

    @Test
    fun `can create and configure element in polymorphic container via delegated property`() {

        val element = DomainObjectBase.Foo()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn element
            on { getByName("domainObject") } doReturn element
        }

        val domainObject by container.creating(type = DomainObjectBase.Foo::class) {
            foo = "domain-foo"
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
        assertThat(
            domainObject.foo,
            equalTo("domain-foo"))
    }

    @Test
    fun `can create element in polymorphic container via delegated property`() {

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn DomainObjectBase.Foo()
        }

        @Suppress("unused_variable")
        val domainObject by container.creating(DomainObjectBase.Foo::class)

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
    }

    @Test
    fun `can create element within configuration block via delegated property`() {
        val tasks = mock<TaskContainer> {
            on { create("hello") } doReturn mock<Task>()
        }

        tasks {
            @Suppress("unused_variable")
            val hello by creating
        }
        verify(tasks).create("hello")
    }

    @Test
    fun `can get element of specific type within configuration block via delegated property`() {
        val tasks = mock<TaskContainer> {
            on { getByName("hello") } doReturn mock<JavaExec>()
        }

        @Suppress("unused_variable")
        tasks {
            val hello: JavaExec by getting
            val ref = hello // forces the element to be accessed
        }
        verify(tasks).getByName("hello")
    }

    @Test
    fun `can create element of specific type within configuration block via delegated property`() {

        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn DomainObjectBase.Foo()
        }

        container {

            @Suppress("unused_variable")
            val domainObject by creating(type = DomainObjectBase.Foo::class)
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
    }

    @Test
    fun `can create and configure element of specific type within configuration block via delegated property`() {

        val element = DomainObjectBase.Foo()
        val container = mock<PolymorphicDomainObjectContainer<DomainObjectBase>> {
            on { create("domainObject", DomainObjectBase.Foo::class.java) } doReturn element
        }

        container {

            @Suppress("unused_variable")
            val domainObject by creating(DomainObjectBase.Foo::class) {
                foo = "domain-foo"
            }
        }

        verify(container).create("domainObject", DomainObjectBase.Foo::class.java)
        assertThat(
            element.foo,
            equalTo("domain-foo"))
    }
}
