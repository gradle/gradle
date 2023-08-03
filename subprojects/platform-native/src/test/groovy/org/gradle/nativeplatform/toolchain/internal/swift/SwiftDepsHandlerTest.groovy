/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@Requires(UnitTestPreconditions.NotWindows)
class SwiftDepsHandlerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    File moduleSwiftDeps
    File barSource
    File fooSource
    File mainSource
    File unknownSource

    SwiftDepsHandler.SwiftDeps original

    @Subject
    def swiftDepsHandler = new SwiftDepsHandler()

    def setup() {
        moduleSwiftDeps = tmpDir.file("module.swiftdeps")
        barSource = tmpDir.file("src/bar.swift").touch()
        fooSource = tmpDir.file("src/foo.swift").touch()
        mainSource = tmpDir.file("src/main.swift").touch()
        unknownSource = tmpDir.file("src/unknown.swift").touch()

        moduleSwiftDeps << """
version: "Swift version 4.0.3 (swift-4.0.3-RELEASE)"
options: "7890c730e32273cd2686f36d1bd976c0"
build_time: [1517422583, 339630833]
inputs:
  "${barSource.absolutePath}": [9223372036, 854775807]
  "${mainSource.absolutePath}": [1517422583, 0]
  "${fooSource.absolutePath}": [1517422583, 0]
"""
        original = swiftDepsHandler.parse(moduleSwiftDeps)
    }

    def "missing module.swiftdeps allows incremental compile"() {
        moduleSwiftDeps.delete()
        expect:
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [barSource])
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [])
    }

    def "corrupted file disables incremental compile"() {
        moduleSwiftDeps.text = "Ceci n'est pas YAML"
        expect:
        !swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [barSource])
        // This is OK because the swiftc compiler will fix corrupted files
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [])
    }

    def "nothing changes if no file changed"() {
        expect:
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [])
        def swiftDeps = swiftDepsHandler.parse(moduleSwiftDeps)
        assertNothingChanged(swiftDeps)
    }

    def "adjusts only provided files"() {
        expect:
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [barSource])
        def swiftDeps = swiftDepsHandler.parse(moduleSwiftDeps)
        assertFileHasResetTimestamp(swiftDeps, barSource)
    }

    def "unknown changed file does not change file"() {
        expect:
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [unknownSource])
        def swiftDeps = swiftDepsHandler.parse(moduleSwiftDeps)
        assertNothingChanged(swiftDeps)
    }

    def "can change all inputs"() {
        expect:
        swiftDepsHandler.adjustTimestampsFor(moduleSwiftDeps, [barSource, fooSource, mainSource])
        def swiftDeps = swiftDepsHandler.parse(moduleSwiftDeps)
        assertFileHasResetTimestamp(swiftDeps, barSource, fooSource, mainSource)
    }

    void assertNothingChanged(SwiftDepsHandler.SwiftDeps current) {
        assert current.build_time == original.build_time
        assert current.version == original.version
        assert current.options == original.options
        assert current.inputs == original.inputs
    }

    void assertFileHasResetTimestamp(SwiftDepsHandler.SwiftDeps current, File... inputs) {
        assert !original.inputs.isEmpty()
        def changedInputs = inputs*.absolutePath

        original.inputs.each { String inputPath, List timestamp ->
            List currentTimestamp = current.inputs.get(inputPath)
            if (inputPath in changedInputs) {
                changedInputs.remove(inputPath)
                assert currentTimestamp == SwiftDepsHandler.RESET_TIMESTAMP
            } else {
                assert currentTimestamp == timestamp
            }
        }

        assert changedInputs.empty
    }
}
