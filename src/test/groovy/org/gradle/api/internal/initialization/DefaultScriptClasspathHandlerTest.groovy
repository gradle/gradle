package org.gradle.api.internal.initialization

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.initialization.DefaultScriptClasspathHandler
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock)
public class DefaultScriptClasspathHandlerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler)
    private final DependencyHandler dependencyHandler = context.mock(DependencyHandler)
    private final DefaultScriptClasspathHandler handler = new DefaultScriptClasspathHandler(repositoryHandler, dependencyHandler)

    @Test void canConfigureRepositories() {
        context.checking {
            one(repositoryHandler).mavenCentral()
        }

        handler.repositories {
            mavenCentral()
        }
    }

    @Test void canConfigureDependencies() {
        context.checking {
            one(dependencyHandler).add('config', 'dep')
        }

        handler.dependencies {
            add('config', 'dep')
        }
    }

}