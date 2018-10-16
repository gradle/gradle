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
package org.gradle.api.publication.maven.internal.pom

import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.artifacts.maven.PomFilterContainer
import org.gradle.api.publication.maven.internal.BasePomFilterContainer
import org.gradle.api.publication.maven.internal.BasePomFilterContainerTest

class DefaultGroovyPomFilterContainerTest extends BasePomFilterContainerTest {
    static final String TEST_NAME = "somename"
    static final String TEST_GROUP = "testGroup"
    PomFilterContainer groovyPomFilterContainer

    protected BasePomFilterContainer createPomFilterContainer() {
        return groovyPomFilterContainer = new BasePomFilterContainer(mavenPomFactoryMock)
    }

    def addFilterWithClosure() {
        when:
        Closure closureFilter = {}
        MavenPom pom = groovyPomFilterContainer.addFilter(TEST_NAME, closureFilter)

        then:
        pom == pomMock
        groovyPomFilterContainer.pom(TEST_NAME) == pomMock
    }

    def filterWithClosure() {
        when:
        Closure closureFilter = {}
        groovyPomFilterContainer.filter(closureFilter)

        then:
        1 * pomFilterMock.setFilter(_)
    }

    def defaultPomWithClosure() {
        when:
        groovyPomFilterContainer.pom {
            groupId = TEST_GROUP
        }

        then:
        1 * pomFilterMock.pomTemplate >> pomMock
        1 * pomMock.setGroupId(TEST_GROUP)
    }

    def pomWithClosure() {
        when:
        groovyPomFilterContainer.addFilter(TEST_NAME, {})
        groovyPomFilterContainer.pom(TEST_NAME) {
            groupId = TEST_GROUP
        }

        then:
        1 * pomMock.setGroupId(TEST_GROUP)
    }
}


