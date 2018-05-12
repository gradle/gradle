package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

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


class NamedDomainObjectCollectionExtensionsTest {

    data class DomainObject(var foo: String? = null)

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

        val container = mock<NamedDomainObjectCollection<Any>> {
            on { getByName("domainObject") } doReturn Object()
        }
        val domainObject: DomainObject by container

        val error = assertFailsWith(IllegalArgumentException::class) {
            println(domainObject)
        }
        assertThat(
            error.message,
            matches("Element 'domainObject' of type 'java\\.lang\\.Object' from container '${Pattern.quote(container.toString())}' cannot be cast to '${Pattern.quote(DomainObject::class.qualifiedName)}'\\."))
    }
}
