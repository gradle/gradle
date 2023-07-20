/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class DynamicMethodLookupIntegrationTest extends AbstractIntegrationSpec {
    @Issue("GRADLE-3460")
    def "extension configuration method is preferred over property with closure value"() {
        given:
        buildFile """
class ContactExtension {
    String prop
}

class ContactPlugin implements Plugin<Project> {
    public void apply(Project project) {
        ContactExtension extension = project.extensions.create('contacts', ContactExtension)
        project.ext.contacts = { String... args ->
            return args
        }
    }
}

apply plugin: ContactPlugin

contacts {
    assert delegate instanceof ContactExtension
    prop = "value"
}
assert extensions.contacts.prop == "value"

assert contacts() == []
assert contacts("a") == [ "a" ]
assert contacts("a", "b", "c") == [ "a", "b", "c" ]
"""
        expect:
        succeeds()
    }

    def "extension configuration method is preferred over property with untyped closure value"() {
        given:
        buildFile """
class ContactExtension {
    String prop
}

extensions.create('contacts', ContactExtension)
ext.contacts = { args ->
    return args
}

contacts {
    assert delegate instanceof ContactExtension
    prop = "value"
}
assert extensions.contacts.prop == "value"

assert contacts() == null
assert contacts("a") == "a"
"""

        expect:
        succeeds()
    }

    // Documents actual behaviour for backwards compatibility, not necessarily desired behaviour
    def "inherited convention method is preferred over property with closure value"() {
        given:
        settingsFile << "include 'child'"
        buildFile """
class ContactConvention {
    def contacts(String arg) { arg }
}

convention.plugins.contacts = new ContactConvention()

subprojects {
    ext.contacts = { throw new RuntimeException() }
    assert contacts("a") == "a"
}
"""

        executer.expectDocumentedDeprecationWarning(
            "The Project.getConvention() method has been deprecated. " +
             "This is scheduled to be removed in Gradle 9.0. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
        )
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.api.plugins.Convention type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
        )

        expect:
        succeeds()
    }

    def "property with closure value is preferred over inherited property with closure value"() {
        given:
        settingsFile << "include 'child'"
        buildFile """
ext.contacts = { throw new RuntimeException() }

subprojects {
    ext.contacts = { it }
    assert contacts("a") == "a"
}
"""

        expect:
        succeeds()
    }
}
