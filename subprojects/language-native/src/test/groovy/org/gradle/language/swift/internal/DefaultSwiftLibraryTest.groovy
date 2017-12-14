/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.internal

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileOperations
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultSwiftLibraryTest extends Specification {
    def api = Stub(TestConfiguration)
    def configurations = Stub(ConfigurationContainer)
    DefaultSwiftLibrary library

    def setup() {
        _ * configurations.maybeCreate("api") >> api
        _ * configurations.maybeCreate(_) >> Stub(TestConfiguration)
        library = new DefaultSwiftLibrary("main", Mock(ProjectLayout), TestUtil.objectFactory(), Stub(FileOperations), configurations)
    }

    def "has api configuration"() {
        expect:
        library.apiDependencies == api
    }

    def "can create static binary"() {
        expect:
        def binary = library.createStaticLibrary("debug", true, false, true)
        binary.name == "mainDebug"
        binary.debuggable
        !binary.optimized
        binary.testable

        library.binaries.realizeNow()
        library.binaries.get() == [binary] as Set
    }

    def "can create shared binary"() {
        expect:
        def binary = library.createSharedLibrary("debug", true, false, true)
        binary.name == "mainDebug"
        binary.debuggable
        !binary.optimized
        binary.testable

        library.binaries.realizeNow()
        library.binaries.get() == [binary] as Set
    }

    def "throws exception when development binary is not available"() {
        given:
        library.binaries.realizeNow()

        when:
        library.developmentBinary.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No value has been specified for this provider."
    }

    def "returns shared, debuggable and not optimized development binary when available"() {
        expect:
        def binary = library.createSharedLibrary("debug", true, false, true)
        library.binaries.realizeNow()
        library.developmentBinary.get() == binary
    }

    interface TestConfiguration extends Configuration, FileCollectionInternal {
    }
}
