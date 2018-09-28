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
package org.gradle.api.publication.maven.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.artifacts.maven.PublishFilter
import org.gradle.internal.Factory
import spock.lang.Specification

import static org.junit.Assert.fail

class BasePomFilterContainerTest extends Specification {
    private static final String TEST_NAME = "testName"

    private BasePomFilterContainer pomFilterContainer
    protected Factory<MavenPom> mavenPomFactoryMock = Mock()
    protected MavenPom pomMock = Mock()
    protected PomFilter pomFilterMock = Mock()
    protected PublishFilter publishFilterMock = Mock()


    protected BasePomFilterContainer createPomFilterContainer() {
        return new BasePomFilterContainer(mavenPomFactoryMock)
    }

    def setup() {
        _ * mavenPomFactoryMock.create() >> pomMock
        pomFilterContainer = createPomFilterContainer()
        pomFilterContainer.setDefaultPomFilter(pomFilterMock)
    }

    def init() {
        when:
        pomFilterContainer = createPomFilterContainer()

        then:
        pomFilterContainer.pom != null
        pomFilterContainer.filter == PublishFilter.ALWAYS_ACCEPT
    }

    def getFilterWithNullName() {
        when:
        pomFilterContainer.filter((String) null)

        then:
        thrown(InvalidUserDataException)
    }

    def getPomWithNullName() {
        when:
        pomFilterContainer.pom((String) null)

        then:
        thrown(InvalidUserDataException)
    }

    def addFilterWithNullName() {
        when:
        pomFilterContainer.addFilter(null, PublishFilter.ALWAYS_ACCEPT)

        then:
        thrown(InvalidUserDataException)
    }

    def addFilterWithNullFilter() {
        when:
        pomFilterContainer.addFilter("somename", (PublishFilter) null)

        then:
        thrown(InvalidUserDataException)
    }

    def getFilter() {
        when:
        _ * pomFilterMock.getFilter() >> publishFilterMock

        then:
        pomFilterContainer.getFilter() == publishFilterMock
    }

    def setFilter() {
        when:
        pomFilterContainer.setFilter(publishFilterMock)

        then:
        1 * pomFilterMock.setFilter(publishFilterMock)
    }

    def getPom() {
        when:
        pomFilterMock.getPomTemplate() >> pomMock

        then:
        pomFilterContainer.getPom() == pomMock
    }

    def setPom() {
        when:
        pomFilterContainer.setPom(pomMock)

        then:
        1 * pomFilterMock.setPomTemplate(pomMock)
    }

    def addFilter() {
        when:
        MavenPom pom = pomFilterContainer.addFilter(TEST_NAME, publishFilterMock)

        then:
        pom == pomMock
        pomMock == pomFilterContainer.pom(TEST_NAME)
        publishFilterMock == pomFilterContainer.filter(TEST_NAME)
    }

    def getActivePomFiltersWithDefault() {
        when:
        Iterator<PomFilter> pomFilterIterator = pomFilterContainer.getActivePomFilters().iterator()

        then:
        pomFilterIterator.next() == pomFilterMock
        !pomFilterIterator.hasNext()
    }

    def getActivePomFiltersWithAdditionalFilters() {
        PublishFilter filter1 = Mock(PublishFilter)
        PublishFilter filter2 = Mock(PublishFilter)
        String testName1 = "name1"
        String testName2 = "name2"

        when:
        pomFilterContainer.addFilter(testName1, filter1)
        pomFilterContainer.addFilter(testName2, filter2)

        then:
        Set<PomFilter> actualActiveFilters = pomFilterContainer.getActivePomFilters() as Set
        actualActiveFilters.size() == 2
        checkIfInSet(testName1, filter1, actualActiveFilters)
        checkIfInSet(testName2, filter2, actualActiveFilters)
    }

    protected void checkIfInSet(String expectedName, PublishFilter expectedPublishFilter, Set<PomFilter> filters) {
        for (PomFilter pomFilter : filters) {
            if (areEqualPomFilter(expectedName, expectedPublishFilter, pomFilter)) {
                return
            }
        }
        fail("Not in Set")
    }

    private boolean areEqualPomFilter(String expectedName, PublishFilter expectedPublishFilter, PomFilter pomFilter) {
        if (!expectedName.equals(pomFilter.getName())) {
            return false
        }
        if (!(expectedPublishFilter == pomFilter.getFilter())) {
            return false
        }
        return true
    }
}
