/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.tasks.javadoc

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.util.internal.WrapUtil

class GroovydocTest extends AbstractConventionTaskTest {
    private Groovydoc groovydoc

    def setup() {
        groovydoc = createTask(Groovydoc.class)
    }

    ConventionTask getTask() {
        return groovydoc
    }

    def "link with null URL is invalid"() {
        when:
        groovydoc.link(null, "package")

        then:
        thrown(InvalidUserDataException)
    }

    def "link with empty package list is invalid"() {
        when:
        groovydoc.link("http://www.abc.de")

        then:
        thrown(InvalidUserDataException)
    }

    def "link with null package is invalid"() {
        when:
        groovydoc.link("http://www.abc.de", "package", null)

        then:
        thrown(InvalidUserDataException)
    }

    def "can add link"() {
        given:
        def url1 = "http://www.url1.de"
        def url2 = "http://www.url2.de"
        def package1 = "package1"
        def package2 = "package2"
        def package3 = "package3"

        groovydoc.link(url1, package1, package2)
        groovydoc.link(url2, package3)

        expect:
        groovydoc.getLinks().get() == WrapUtil.toSet(
                new Groovydoc.Link(url1, package1, package2),
                new Groovydoc.Link(url2, package3))
    }

    def "can set links"() {
        given:
        def url1 = "http://www.url1.de"
        def url2 = "http://www.url2.de"
        def package1 = "package1"
        def package2 = "package2"

        groovydoc.link(url1, package1)
        def newLinkSet = WrapUtil.toSet(new Groovydoc.Link(url2, package2))
        groovydoc.links = newLinkSet

        expect:
        groovydoc.getLinks().get() == newLinkSet
    }

    def "groovy classpath must not be empty"() {
        when:
        groovydoc.groovyClasspath = TestFiles.empty()
        groovydoc.generate()

        then:
        thrown(InvalidUserDataException)
    }
}
