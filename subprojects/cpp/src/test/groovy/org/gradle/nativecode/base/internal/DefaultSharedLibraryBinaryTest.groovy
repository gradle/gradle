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
    def "has useful string representation"() {
        def library = Stub(Library) {
            getName() >> "main"
        }
        def binary = new DefaultSharedLibraryBinary(library)

        expect:
        binary.toString() == "shared library 'main'"
    }

    def "can convert binary to a native dependency"() {
        def headers = Stub(SourceDirectorySet)
        def library = Stub(Library) {
            getHeaders() >> headers
            getName() >> "main"
        }
        def binary = new DefaultSharedLibraryBinary(library)
        binary.builtBy(Stub(Task))

        expect:
        def nativeDependency = binary.asNativeDependencySet
        nativeDependency.files.files == [binary.outputFile] as Set
        nativeDependency.files.buildDependencies == binary.buildDependencies
        nativeDependency.files.toString() == "shared library 'main'"
        nativeDependency.includeRoots == headers
    }
}
