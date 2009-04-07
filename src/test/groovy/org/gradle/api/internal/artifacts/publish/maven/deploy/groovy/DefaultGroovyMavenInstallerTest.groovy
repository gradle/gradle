/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.gradle.api.internal.artifacts.publish.maven.deploy.groovy

import org.gradle.api.artifacts.maven.GroovyPomFilterContainer
import org.gradle.api.artifacts.maven.PomFilterContainer
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstallerTest
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenInstaller
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock.class)
class DefaultGroovyMavenInstallerTest extends BaseMavenInstallerTest {

    private DefaultGroovyMavenInstaller groovyMavenInstaller;

    protected PomFilterContainer createPomFilterContainerMock() {
        context.mock(GroovyPomFilterContainer.class);
    }

    protected BaseMavenInstaller createMavenInstaller() {
        groovyMavenInstaller = new DefaultGroovyMavenInstaller(TEST_NAME, pomFilterContainerMock, artifactPomContainerMock, configurationContainerMock)
    }

    @Before
    void setUp() {
        super.setUp();
    }

    @Test
    void filter() {
        Closure testClosure = {}
        context.checking {
            one(pomFilterContainerMock).filter(testClosure)
        }
        groovyMavenInstaller.filter(testClosure)
    }

    @Test
    void pom() {
        Closure testClosure = {}
        context.checking {
            one(pomFilterContainerMock).pom(testClosure)
        }
        groovyMavenInstaller.pom(testClosure)
    }

    @Test
    void pomWithName() {
        Closure testClosure = {}
        String testName = 'somename'
        context.checking {
            one(pomFilterContainerMock).pom(testName, testClosure)
        }
        groovyMavenInstaller.pom(testName, testClosure)
    }

    @Test
    void addFilter() {
        Closure testClosure = {}
        String testName = 'somename'
        context.checking {
            one(pomFilterContainerMock).addFilter(testName, testClosure)
        }
        groovyMavenInstaller.addFilter(testName, testClosure)
    }
}
