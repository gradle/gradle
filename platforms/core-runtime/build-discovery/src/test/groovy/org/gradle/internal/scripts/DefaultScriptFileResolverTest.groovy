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

    /**
     * If this test breaks, it means a new scripting language has been added.
     * It's important that this test covers all accepted extensions.
     *
     * Please update the tests in this file, and add the new extension to the list below.
     */
    def "list of extensions are what we expect"() {
        ScriptingLanguages.all().collect {it.extension} == [
            ".gradle",
            ".gradle.kts",
            ".gradle.dcl"
        ]
    }

    def "all known extensions should be recognized"() {
        given:
        def selected = new File(testDir, "build.gradle")
        selected.createNewFile()

        def recognizedButIgnored = [
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
        result.ignoredCandidates == recognizedButIgnored

    }

    def "when no script file is found, resolution result should be empty"() {
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

    def "listener should only be notified once, not for each extension checked"() {
        given:
        new File(testDir, "build.gradle").createNewFile()
        new File(testDir, "build.gradle.kts").createNewFile()
        new File(testDir, "build.gradle.dcl").createNewFile()

        def notificationCount = 0
        def listener = new ScriptFileResolvedListener() {
            @Override
            void onScriptFileResolved(File scriptFile) {
                notificationCount++
            }
        }

        when:
        def resolver = new DefaultScriptFileResolver(listener)
        resolver.resolveScriptFile(testDir, "build")

        then:
        notificationCount == 1
    }

}
