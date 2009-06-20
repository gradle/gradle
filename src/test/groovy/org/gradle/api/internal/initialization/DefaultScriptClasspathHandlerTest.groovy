package org.gradle.api.internal.initialization

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.initialization.DefaultScriptClasspathHandler
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock)
public class DefaultScriptClasspathHandlerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler)
    private final DependencyHandler dependencyHandler = context.mock(DependencyHandler)
    private final ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer)
    private final Configuration configuration = context.mock(Configuration)
    private final ClassLoader parentClassLoader = [:] as ClassLoader

    @Test void addsClasspathConfiguration() {
        context.checking {
            one(configurationContainer).add('classpath')
        }

        new DefaultScriptClasspathHandler(repositoryHandler, dependencyHandler, configurationContainer, parentClassLoader)
    }

    @Test void createsAClassLoaderAndAddsContentsOfClassPathConfiguration() {
        DefaultScriptClasspathHandler handler = handler()

        ClassLoader classLoader = handler.classLoader
        assertThat(classLoader, instanceOf(URLClassLoader))
        assertThat(classLoader.parent, sameInstance(parentClassLoader))
        assertThat(classLoader.getURLs(), isEmptyArray())

        File file1 = new File('a')
        File file2 = new File('b')
        context.checking {
            one(configuration).getFiles()
            will(returnValue(WrapUtil.toSet(file1, file2)))
        }

        handler.updateClassPath()

        assertThat(handler.classLoader, sameInstance(classLoader))
        assertThat(classLoader.getURLs(), equalTo([file1.toURL(), file2.toURL()] as URL[]))
    }

    @Test void canConfigureRepositories() {
        DefaultScriptClasspathHandler handler = handler()

        context.checking {
            one(repositoryHandler).mavenCentral()
        }

        handler.repositories {
            mavenCentral()
        }
    }

    @Test void canConfigureDependencies() {
        DefaultScriptClasspathHandler handler = handler()

        context.checking {
            one(dependencyHandler).add('config', 'dep')
        }

        handler.dependencies {
            add('config', 'dep')
        }
    }

    private DefaultScriptClasspathHandler handler() {
        context.checking {
            one(configurationContainer).add('classpath')
            will(returnValue(configuration))
        }
        DefaultScriptClasspathHandler handler = new DefaultScriptClasspathHandler(repositoryHandler, dependencyHandler, configurationContainer, parentClassLoader)
        return handler
    }
}