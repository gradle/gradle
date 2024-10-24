package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.only
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.util.TestUtil
import org.junit.Test
import org.mockito.invocation.InvocationOnMock
import java.net.URI


class RepositoryHandlerExtensionsTest {

    @Test
    fun `#maven(String)`() {

        val repository = mock<MavenArtifactRepository> {
            on(it.url).doReturn(TestUtil.propertyFactory().property(URI::class.java))
        }
        val repositories = mavenRepositoryHandlerMockFor(repository)

        val url = URI.create("some://uri")
        repositories {
            maven(url = url)
        }

        verify(repository, only()).url
    }

    @Test
    fun `#maven(String, Action) sets url before invoking configuration action`() {

        val repository = mock<MavenArtifactRepository> {
            on(it.url).doReturn(TestUtil.propertyFactory().property(URI::class.java))
        }
        val repositories = mavenRepositoryHandlerMockFor(repository)

        val url = URI.create("some://uri")
        repositories {
            maven(url = url) {
                verify(repository).url
                name = "repo name"
            }
        }

        verify(repository).name = "repo name"
    }

    @Test
    fun `#ivy(String)`() {

        val repository = mock<IvyArtifactRepository> {
            on(it.url).doReturn(TestUtil.propertyFactory().property(URI::class.java))
        }
        val repositories = ivyRepositoryHandlerMockFor(repository)

        val url = URI.create("some://uri")
        repositories {
            ivy(url = url)
        }

        verify(repository, only()).url
    }

    @Test
    fun `#ivy(String, Action) sets url before invoking configuration action`() {

        val repository = mock<IvyArtifactRepository> {
            on(it.url).doReturn(TestUtil.propertyFactory().property(URI::class.java))
        }
        val repositories = ivyRepositoryHandlerMockFor(repository)

        val url = URI.create("some://uri")
        repositories {
            ivy(url = url) {
                verify(repository).url
                name = "repo name"
            }
        }

        verify(repository).name = "repo name"
    }

    private
    inline operator fun RepositoryHandler.invoke(action: RepositoryHandler.() -> Unit) = apply(action)

    private
    fun mavenRepositoryHandlerMockFor(repository: MavenArtifactRepository) = mock<RepositoryHandler> {
        on { maven(any<Action<MavenArtifactRepository>>()) }.then {
            it.configureWithAction(repository)
        }
    }

    private
    fun ivyRepositoryHandlerMockFor(repository: IvyArtifactRepository) = mock<RepositoryHandler> {
        on { ivy(any<Action<IvyArtifactRepository>>()) }.then {
            it.configureWithAction(repository)
        }
    }

    private
    fun <T : Any> InvocationOnMock.configureWithAction(repository: T): T = repository.also {
        getArgument<Action<T>>(0).execute(it)
    }
}
