/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import spock.lang.Specification

class SharedJavaInstallationRegistryTest extends Specification {

    def "registry keeps track of newly added installations"() {
        given:
        def registry = new SharedJavaInstallationRegistry()

        when:
        registry.add("foo", new File("bar"))

        then:
        registry.listInstallations() == [foo: new File("bar")]
    }

    def "registry cannot be mutated after finalizing"() {
        given:
        def registry = new SharedJavaInstallationRegistry()
        registry.add("foo", new File("bar"))

        when:
        registry.finalizeValue()
        registry.add("foo2", new File("bar"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "accessing the list of installations finalizes it"() {
        given:
        def registry = new SharedJavaInstallationRegistry()
        registry.add("foo", new File("bar"))

        when:
        registry.listInstallations()
        registry.add("foo2", new File("bar"))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "list of installations is cached"() {
        given:
        def registry = new SharedJavaInstallationRegistry()
        registry.add("foo", new File("bar"))
        registry.add("foo2", new File("bar"))

        when:
        def installations = registry.listInstallations();
        def installations2 = registry.listInstallations();

        then:
        installations.is(installations2)
    }

}
