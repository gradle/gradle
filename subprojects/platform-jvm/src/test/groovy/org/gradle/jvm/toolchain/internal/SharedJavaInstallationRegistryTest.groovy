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

import org.gradle.api.file.Directory
import spock.lang.Specification

import static InstallationSuppliers.forDirectory

class SharedJavaInstallationRegistryTest extends Specification {

    def registry = new SharedJavaInstallationRegistry()

    def "registry keeps track of newly added installations"() {
        when:
        def path = Mock(Directory)
        registry.add(forDirectory(path))

        then:
        registry.listInstallations() == [path] as Set
    }

    def "registry cannot be mutated after finalizing"() {
        given:
        registry.add(forDirectory(Mock(Directory)))
        registry.add(forDirectory(Mock(Directory)))

        when:
        registry.finalizeValue()
        registry.add(forDirectory(Mock(Directory)))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "accessing the list of installations finalizes it"() {
        given:
        registry.add(forDirectory(Mock(Directory)))

        when:
        registry.listInstallations()
        registry.add(forDirectory(Mock(Directory)))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "list of installations is cached"() {
        given:
        registry.add(forDirectory(Mock(Directory)))
        registry.add(forDirectory(Mock(Directory)))

        when:
        def installations = registry.listInstallations();
        def installations2 = registry.listInstallations();

        then:
        installations.is(installations2)
    }

}
