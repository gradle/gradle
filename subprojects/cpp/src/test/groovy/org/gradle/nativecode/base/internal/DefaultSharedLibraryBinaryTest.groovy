/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.internal

import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.nativecode.base.Library
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultSharedLibraryBinaryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    final toolChain = Stub(ToolChainInternal)
    final library = Stub(Library) {
        getName() >> "main"
    }
    final binary = new DefaultSharedLibraryBinary(library, new DefaultFlavor("default"), toolChain)
    final flavoredBinary = new DefaultSharedLibraryBinary(library, new DefaultFlavor("flavor"), toolChain)

    def "has useful string representation"() {
        expect:
        binary.toString() == "shared library 'main'"
        flavoredBinary.toString() == "shared library 'flavorMain'"
    }

    def "can convert binary to a native dependency"() {
        given:
        def binaryFile = tmpDir.createFile("binary.run")
        def linkFile = tmpDir.createFile("binary.link")
        def lifecycleTask = Stub(Task)
        binary.setLifecycleTask(lifecycleTask)
        binary.dependsOn(Stub(Task))
        binary.outputFile = binaryFile

        and:
        def headers = Stub(SourceDirectorySet)
        library.headers >> headers
        toolChain.getSharedLibraryLinkFileName(binaryFile.path) >> linkFile.path

        expect:
        def nativeDependency = binary.resolve()
        nativeDependency.includeRoots == headers

        and:
        nativeDependency.linkFiles.files == [linkFile] as Set
        nativeDependency.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.linkFiles.toString() == "shared library 'main'"

        and:
        nativeDependency.runtimeFiles.files == [binaryFile] as Set
        nativeDependency.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.runtimeFiles.toString() == "shared library 'main'"
    }
}
