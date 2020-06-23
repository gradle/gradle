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

import org.gradle.api.logging.Logger
import spock.lang.Specification
import spock.lang.Unroll

class SharedJavaInstallationRegistryTest extends Specification {

    def registry = new SharedJavaInstallationRegistry(Collections.emptyList())
    def tempFolder = createTempDir();

    def "registry keeps track of newly added installations"() {
        when:
        registry.add(forDirectory(tempFolder))

        then:
        registry.listInstallations() == [tempFolder] as Set
    }

    def "registry filters non-unique locations"() {
        when:
        registry.add(forDirectory(tempFolder))
        registry.add(forDirectory(tempFolder))

        then:
        registry.listInstallations() == [tempFolder] as Set
    }

    def "can be initialized with suppliers"() {
        given:
        def tmpDir2 = createTempDir()
        def tmpDir3 = createTempDir()

        when:
        def registry = new SharedJavaInstallationRegistry([forDirectory(tmpDir2), forDirectory(tmpDir3)]);
        registry.add(forDirectory(tempFolder))

        then:
        registry.listInstallations().containsAll(tempFolder, tmpDir2, tmpDir2)
    }

    def "registry cannot be mutated after finalizing"() {
        given:
        registry.add(forDirectory(tempFolder))
        registry.add(forDirectory(tempFolder))

        when:
        registry.finalizeValue()
        registry.add(forDirectory(tempFolder))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "accessing the list of installations finalizes it"() {
        given:
        registry.add(forDirectory(tempFolder))

        when:
        registry.listInstallations()
        registry.add(forDirectory(tempFolder))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Installation must not be mutated after being finalized"
    }

    def "list of installations is cached"() {
        given:
        registry.add(forDirectory(tempFolder))
        registry.add(forDirectory(tempFolder))

        when:
        def installations = registry.listInstallations();
        def installations2 = registry.listInstallations();

        then:
        installations.is(installations2)
    }

    @Unroll
    def "warns and filters invalid installations, exists: #exists, directory: #directory"() {
        given:
        def file = Mock(File)
        file.exists() >> exists
        file.isDirectory() >> directory
        file.absolutePath >> path
        def logger = Mock(Logger)
        def registry = SharedJavaInstallationRegistry.withLogger(logger)
        registry.add(forDirectory(file))

        when:
        def installations = registry.listInstallations()

        then:
        installations.isEmpty()
        1 * logger.warn(logOutput, "'" + path + "' (testSource)")

        where:
        path        | exists | directory | valid | logOutput
        '/unknown'  | false  | null      | false | 'Directory {} used for java installations does not exist'
        '/foo/file' | true   | false     | false | 'Path for java installation {} points to a file, not a directory'
    }

    InstallationSupplier forDirectory(File directory) {
        {
            it -> Collections.singleton(new InstallationLocation(directory, "testSource"))
        }
    }

    def File createTempDir() {
        def file = File.createTempDir()
        file.deleteOnExit()
        file
    }
}
