package org.gradle.script.lang.kotlin

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.NamedDomainObjectCollection

import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.util.regex.Pattern

import kotlin.test.assertFailsWith

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
    fun `accessing existing element with wrong type gives proper error message`() {

        val container = mock<NamedDomainObjectCollection<Any>> {
            on { getByName("domainObject") } doReturn Object()
        }
        val domainObject: DomainObject by container

        val error = assertFailsWith(IllegalStateException::class) {
            println(domainObject)
        }
        assertThat(
            error.message,
            matches("Element 'domainObject' of type 'java\\.lang\\.Object' from container '${Pattern.quote(container.toString())}' cannot be cast to '${Pattern.quote(DomainObject::class.qualifiedName)}'\\."))
    }
}
