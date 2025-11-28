/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.scripts

import spock.lang.Specification
import spock.lang.TempDir

class DefaultScriptFileResolverTest extends Specification {

    @TempDir
    File testDir

    def "script resolution result should not have undeclared extensions"() {
        given:
        def selected = new File(testDir, "build.gradle")
        selected.createNewFile()

        def knownButIgnored = [
            "build.gradle.kts", "build.gradle.dcl"
        ].collect {
            def f = new File(testDir, it)
            f.createNewFile()
            f
        }

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "build")

        then:
        result.selectedCandidate == selected
        result.ignoredCandidates == knownButIgnored

    }

    def "when no script file is found, resolution result should reflect that"() {
        given:
        ["settings.gradle", "settings.gradle.kts"].each {
            new File(testDir, it).createNewFile()
        }

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "build")

        then:
        result.selectedCandidate == null
        result.ignoredCandidates.isEmpty()
    }

}
