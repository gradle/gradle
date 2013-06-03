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

import org.gradle.api.file.SourceDirectorySet
import org.gradle.nativecode.base.Library
import org.gradle.tooling.model.Task
import spock.lang.Specification

class DefaultSharedLibraryBinaryTest extends Specification {
    final toolChain = Stub(ToolChainInternal)
    def library = Stub(Library) {
        getName() >> "main"
    }
    def binary = new DefaultSharedLibraryBinary(library, toolChain)

    def "has useful string representation"() {
        expect:
        binary.toString() == "shared library 'main'"
    }

    def "can convert binary to a native dependency"() {
        given:
        def headers = Stub(SourceDirectorySet)
        library.headers >> headers
        binary.builtBy(Stub(Task))

        expect:
        def nativeDependency = binary.asNativeDependencySet
        nativeDependency.includeRoots == headers

        and:
        nativeDependency.linkFiles.files == [binary.outputFile] as Set
        nativeDependency.linkFiles.buildDependencies == binary.buildDependencies
        nativeDependency.linkFiles.toString() == "shared library 'main'"

        and:
        nativeDependency.runtimeFiles.files == [binary.outputFile] as Set
        nativeDependency.runtimeFiles.buildDependencies == binary.buildDependencies
        nativeDependency.runtimeFiles.toString() == "shared library 'main'"
    }
}
