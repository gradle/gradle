package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.reflect.KClass


class NamedDomainObjectCollectionExtensionsTest {

    data class DomainObject(var foo: String? = null, var bar: String? = null)

    @Test
    fun `reified withType extension returns named collection`() {

        val element = DomainObject()
        val elementProvider = mockDomainObjectProviderFor(element)
        val typedCollection = mock<NamedDomainObjectCollection<DomainObject>> {
            on { named("domainObject") } doReturn elementProvider
        }
        val container = mock<NamedDomainObjectCollection<Any>> {
            on { withType(DomainObject::class.java) } doReturn typedCollection
        }
        assertThat(
            container.withType<DomainObject>().named("domainObject").get(),
            sameInstance(element)
        )
    }

    @Test
    fun `can getByName via indexer`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectCollection<DomainObject>> {
            on { getByName("domainObject") } doReturn element
        }
        assertThat(
            container["domainObject"],
            sameInstance(element)
        )
    }

    @Test
    fun `can lazily access and configure existing element by name and type`() {

        val fooObject = DomainObject()
        val fooProvider = mockDomainObjectProviderFor(fooObject)

        val barObject = DomainObject()
        val barProvider = mockDomainObjectProviderFor(barObject)

        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { named("foo", DomainObject::class.java) } doReturn fooProvider
            onNamedWithAction("foo", DomainObject::class, fooProvider)
            on { named("bar", DomainObject::class.java) } doReturn barProvider
            onNamedWithAction("bar", DomainObject::class, barProvider)
            on { getByName("foo") } doReturn fooObject
            on { getByName("bar") } doReturn barObject
        }

        container.named<DomainObject>("foo").configure {
            foo = "reified access"
        }
        container.named<DomainObject>("foo") {
            bar = "reified configuration"
        }

        container.named("bar", DomainObject::class).configure {
            foo = "typed access"
        }
        container.named("bar", DomainObject::class) {
            bar = "typed configuration"
        }

        assertThat(container["foo"].foo, equalTo("reified access"))
        assertThat(container["foo"].bar, equalTo("reified configuration"))

        assertThat(container["bar"].foo, equalTo("typed access"))
        assertThat(container["bar"].bar, equalTo("typed configuration"))
    }
}


internal
fun <T : Any, U : T> KStubbing<PolymorphicDomainObjectContainer<T>>.onRegisterWithAction(name: String, type: KClass<U>, domainObjectProvider: NamedDomainObjectProvider<U>) {
    on { register(eq(name), eq(type.java), any<Action<U>>()) } doAnswer {
        it.getArgument<Action<U>>(2).execute(domainObjectProvider.get())
        domainObjectProvider
    }
}


internal
inline fun <reified T : Any> mockDomainObjectProviderFor(domainObject: T): NamedDomainObjectProvider<T> =
    mock {
        on { get() } doReturn domainObject
        on { configure(any()) } doAnswer {
            it.getArgument<Action<T>>(0).execute(domainObject)
        }
    }


internal
fun <T : Any, U : T> KStubbing<NamedDomainObjectContainer<T>>.onNamedWithAction(name: String, type: KClass<U>, provider: NamedDomainObjectProvider<U>) {
    on { named(eq(name), eq(type.java), any<Action<U>>()) } doAnswer {
        provider.configure(it.getArgument(2))
        provider
    }
}
