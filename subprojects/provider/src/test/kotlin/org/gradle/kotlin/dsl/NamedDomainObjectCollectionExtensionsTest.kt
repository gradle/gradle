package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.Action
import org.gradle.api.DomainObjectProvider
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer

import org.gradle.kotlin.dsl.fixtures.assertFailsWith
import org.gradle.kotlin.dsl.fixtures.matches

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.util.regex.Pattern
import kotlin.reflect.KClass


class NamedDomainObjectCollectionExtensionsTest {

    data class DomainObject(var foo: String? = null, var bar: String? = null)

    @Test
    fun `can access existing element via indexer`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectCollection<DomainObject>> {
            on { getByName("domainObject") } doReturn element
        }
        assertThat(
            container["domainObject"],
            sameInstance(element))
    }

    @Test
    fun `can lazily access and configure existing element by name and type`() {

        val fooObject = DomainObject()
        val fooProvider = mockDomainObjectProviderFor(fooObject)

        val barObject = DomainObject()
        val barProvider = mockDomainObjectProviderFor(barObject)

        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { named("foo") } doReturn fooProvider
            on { named("bar") } doReturn barProvider
            on { getByName("foo") } doReturn fooObject
            on { getByName("bar") } doReturn barObject
        }

        container.named<DomainObject>("foo").configure {
            it.foo = "reified access"
        }
        container.named<DomainObject>("foo") {
            bar = "reified configuration"
        }

        container.named("bar", DomainObject::class).configure {
            it.foo = "typed access"
        }
        container.named("bar", DomainObject::class) {
            bar = "typed configuration"
        }

        assertThat(container["foo"].foo, equalTo("reified access"))
        assertThat(container["foo"].bar, equalTo("reified configuration"))

        assertThat(container["bar"].foo, equalTo("typed access"))
        assertThat(container["bar"].bar, equalTo("typed configuration"))
    }

    @Test
    fun `can access existing element via delegated property`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectCollection<DomainObject>> {
            on { getByName("domainObject") } doReturn element
        }
        val domainObject by container

        assertThat(
            domainObject.foo, // just to prove domainObject's type is inferred correctly
            nullValue())

        assertThat(
            domainObject,
            sameInstance(element))
    }

    @Test
    fun `can access existing element by getting`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { getByName("domainObject") } doReturn element
        }

        container { // invoke syntax
            val domainObject by getting
            assertThat(domainObject, sameInstance(element))
        }

        container.apply { // regular syntax
            val domainObject by getting
            assertThat(domainObject, sameInstance(element))
        }
    }

    @Test
    fun `can configure existing element by getting`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { getByName("domainObject") } doReturn element
        }

        container { // invoke syntax
            @Suppress("unused_variable")
            val domainObject by getting { foo = "foo" }
            assertThat(element.foo, equalTo("foo"))
        }

        container.apply { // regular syntax
            @Suppress("unused_variable")
            val domainObject by getting { foo = "bar" }
            assertThat(element.foo, equalTo("bar"))
        }
    }

    @Test
    fun `can add element by creating`() {

        val fooObject = DomainObject()
        val barObject = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("foo") } doReturn fooObject
            on { getByName("foo") } doReturn fooObject
            on { create("bar") } doReturn barObject
            on { getByName("bar") } doReturn barObject
        }

        container { // invoke syntax
            val foo by creating
            assertThat(foo.foo, nullValue())
        }

        container.apply { // regular syntax
            val bar by creating
            assertThat(bar.foo, nullValue())
        }
    }

    @Test
    fun `can add and configure element by creating`() {

        val fooObject = DomainObject()
        val barObject = DomainObject()
        val container = mock<NamedDomainObjectContainer<DomainObject>> {
            on { create("foo") } doReturn fooObject
            on { getByName("foo") } doReturn fooObject
            on { create("bar") } doReturn barObject
            on { getByName("bar") } doReturn barObject
        }

        container { // invoke syntax
            val foo by creating { foo = "foo" }
            assertThat(foo.foo, equalTo("foo"))
        }

        container.apply { // regular syntax
            val bar by creating { foo = "bar" }
            assertThat(bar.foo, equalTo("bar"))
        }
    }

    @Test
    fun `can access existing typed element by getting`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<Any>> {
            on { getByName("domainObject") } doReturn element
        }

        container { // invoke syntax
            val domainObject: DomainObject by getting
            assertThat(domainObject, sameInstance(element))
        }

        container.apply { // regular syntax
            val domainObject: DomainObject by getting
            assertThat(domainObject, sameInstance(element))
        }
    }

    @Test
    fun `can access existing element by getting with type`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<Any>> {
            on { getByName("domainObject") } doReturn element
        }

        container { // invoke syntax
            val domainObject by getting(DomainObject::class)
            assertThat(domainObject, sameInstance(element))
        }

        container.apply { // regular syntax
            val domainObject by getting(DomainObject::class)
            assertThat(domainObject, sameInstance(element))
        }
    }

    @Test
    fun `can configure existing typed element by getting`() {

        val element = DomainObject()
        val container = mock<NamedDomainObjectContainer<Any>> {
            on { getByName("domainObject") } doReturn element
        }

        container { // invoke syntax
            @Suppress("unused_variable")
            val domainObject by getting(DomainObject::class) { foo = "foo" }
            assertThat(element.foo, equalTo("foo"))
        }

        container.apply { // regular syntax
            @Suppress("unused_variable")
            val domainObject by getting(DomainObject::class) { foo = "bar" }
            assertThat(element.foo, equalTo("bar"))
        }
    }

    @Test
    fun `can add typed element by creating`() {

        val fooObject = DomainObject()
        val barObject = DomainObject()
        val container = mock<PolymorphicDomainObjectContainer<Any>> {
            on { create("foo", DomainObject::class.java) } doReturn fooObject
            on { getByName("foo") } doReturn fooObject
            on { create("bar", DomainObject::class.java) } doReturn barObject
            on { getByName("bar") } doReturn barObject
        }

        container { // invoke syntax
            val foo by creating(DomainObject::class)
            assertThat(foo.foo, nullValue())
        }

        container.apply { // regular syntax
            val bar by creating(DomainObject::class)
            assertThat(bar.foo, nullValue())
        }
    }

    @Test
    fun `can add and configure typed element by creating`() {

        val fooObject = DomainObject()
        val barObject = DomainObject()
        val container = mock<PolymorphicDomainObjectContainer<Any>> {
            on { create("foo", DomainObject::class.java) } doReturn fooObject
            on { getByName("foo") } doReturn fooObject
            on { create("bar", DomainObject::class.java) } doReturn barObject
            on { getByName("bar") } doReturn barObject
        }

        container { // invoke syntax
            val foo by creating(DomainObject::class) { foo = "foo" }
            assertThat(foo.foo, equalTo("foo"))
        }

        container.apply { // regular syntax
            val bar by creating(DomainObject::class) { foo = "bar" }
            assertThat(bar.foo, equalTo("bar"))
        }
    }

    @Test
    fun `accessing existing element with wrong type gives proper error message`() {

        val obj = Object()
        val provider = mockDomainObjectProviderFor<Any>(obj)
        val container = mock<NamedDomainObjectCollection<Any>> {
            on { named("domainObject") } doReturn provider
            on { getByName("domainObject") } doReturn obj
        }

        fun assertFailsWithIllegalElementType(type: KClass<*>, block: () -> Unit) {
            val error = assertFailsWith(IllegalArgumentException::class, block)
            assertThat(
                error.message,
                matches("Element 'domainObject' of type 'java\\.lang\\.Object' from container '${Pattern.quote(container.toString())}' cannot be cast to '${Pattern.quote(type.qualifiedName)}'\\."))
        }

        assertFailsWithIllegalElementType(DomainObject::class) {
            container.named<DomainObject>("domainObject").get()
        }

        val domainObject: DomainObject by container
        assertFailsWithIllegalElementType(DomainObject::class) {
            println(domainObject)
        }
    }
}


private
inline fun <reified T : Any> mockDomainObjectProviderFor(domainObject: T): DomainObjectProvider<T> =
    mock {
        on { get() } doReturn domainObject
        on { configure(any()) }.thenAnswer {
            it.getArgument<Action<T>>(0).execute(domainObject)
        }
    }
