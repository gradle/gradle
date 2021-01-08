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
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification
import spock.lang.Unroll

class JavaInstallationRegistryTest extends Specification {

    def tempFolder = createTempDir()

    def "registry keeps track of initial installations"() {
        when:
        def registry = newRegistry(tempFolder)

        then:
        registry.listInstallations()*.location == [tempFolder]
        registry.listInstallations()*.source == ["testSource"]
    }

    def "registry filters non-unique locations"() {
        when:
        def registry = newRegistry(tempFolder, tempFolder)

        then:
        registry.listInstallations()*.location == [tempFolder]
    }

    def "duplicates are detected using canonical form"() {
        given:
        def registry = newRegistry(tempFolder, new File(tempFolder, "/."))

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location == [tempFolder]
    }

    def "can be initialized with suppliers"() {
        given:
        def tmpDir2 = createTempDir()
        def tmpDir3 = createTempDir()

        when:
        def registry = newRegistry(tempFolder, tmpDir2, tmpDir3)

        then:
        registry.listInstallations()*.location.containsAll(tempFolder, tmpDir2, tmpDir2)
    }

    def "list of installations is cached"() {
        given:
        def registry = newRegistry(tempFolder)

        when:
        def installations = registry.listInstallations()
        def installations2 = registry.listInstallations()

        then:
        installations.is(installations2)
    }

    def "normalize installations to account for macOS folder layout"() {
        given:
        def expectedHome = new File(tempFolder, "Contents/Home")
        assert expectedHome.mkdirs()
        def registry = new JavaInstallationRegistry([forDirectory(tempFolder)], new TestBuildOperationExecutor(), OperatingSystem.MAC_OS)

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(expectedHome)
    }

    def "normalize installations to account for standalone jre"() {
        given:
        def expectedHome = new File(tempFolder, "jre")
        assert expectedHome.mkdirs()
        def jreBinFolder = new File(expectedHome, "bin")
        assert jreBinFolder.mkdir()
        assert new File(jreBinFolder, OperatingSystem.current().getExecutableName( "java")).createNewFile()

        def registry = new JavaInstallationRegistry([forDirectory(tempFolder)], new TestBuildOperationExecutor(), OperatingSystem.current())

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(expectedHome)
    }

    def "skip path normalization on non-osx systems"() {
        given:
        def rootWithMacOsLayout = createTempDir()
        def expectedHome = new File(rootWithMacOsLayout, "Contents/Home")
        assert expectedHome.mkdirs()
        def registry = new JavaInstallationRegistry([forDirectory(rootWithMacOsLayout)], new TestBuildOperationExecutor(), OperatingSystem.LINUX)

        when:
        def installations = registry.listInstallations()

        then:
        installations*.location.contains(rootWithMacOsLayout)
    }

    def "detecting installations is tracked as build operation"() {
        def executor = new TestBuildOperationExecutor()
        given:
        def registry = new JavaInstallationRegistry(Collections.emptyList(), executor, OperatingSystem.current())

        when:
        registry.listInstallations()

        then:
        executor.log.getDescriptors().find { it.displayName == "Toolchain detection"}
    }

    @Unroll
    def "warns and filters invalid installations, exists: #exists, directory: #directory"() {
        given:
        def file = Mock(File)
        file.exists() >> exists
        file.isDirectory() >> directory
        file.absolutePath >> path
        def logger = Mock(Logger)
        def registry = JavaInstallationRegistry.withLogger([forDirectory(file)], logger, new TestBuildOperationExecutor())

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
        { it -> Collections.singleton(new InstallationLocation(directory, "testSource")) }
    }

    File createTempDir() {
        def file = File.createTempDir()
        file.deleteOnExit()
        file.canonicalFile
    }

    private JavaInstallationRegistry newRegistry(File... location) {
        new JavaInstallationRegistry(location.collect { forDirectory(it) }, new TestBuildOperationExecutor(), OperatingSystem.current())
    }
}
