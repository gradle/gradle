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
        def selected = createFiles(testDir, ["build.gradle"])[0]
        def recognizedButIgnored = createFiles(testDir, ["build.gradle.kts", "build.gradle.dcl"])

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "build")

        then:
        result.selectedCandidate == selected
        result.ignoredCandidates == recognizedButIgnored

    }

    def "when no script file is found, resolution result should be empty"() {
        given:
        createFiles(testDir, ["settings.gradle", "settings.gradle.kts"])

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "build")

        then:
        result.selectedCandidate == null
        result.ignoredCandidates.isEmpty()
    }

    def "listener should only be notified once, not for each extension checked"() {
        given:
        createFiles(testDir, ["build.gradle", "build.gradle.kts", "build.gradle.dcl"])
        def listener = new CountingListener()

        when:
        def resolver = new DefaultScriptFileResolver(listener)
        resolver.resolveScriptFile(testDir, "build")

        then:
        listener.count == 1
    }

    def "custom-named script files can be resolved"() {
        given:
        def selected = createFiles(testDir, ["custom.gradle"])[0]
        def recognizedButIgnored = createFiles(testDir, ["custom.gradle.kts", "custom.gradle.dcl"])

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "custom")

        then:
        result.selectedCandidate == selected
        result.ignoredCandidates == recognizedButIgnored
    }

    def "listener should only be notified once for custom-named build files"() {
        given:
        createFiles(testDir, ["a.gradle", "a.gradle.kts", "a.gradle.dcl"])
        def listener = new CountingListener()

        when:
        def resolver = new DefaultScriptFileResolver(listener)
        resolver.resolveScriptFile(testDir, "a")

        then:
        listener.count == 1
    }

    def "listener will be notified with all the possible files before selected"() {
        given:
        createFiles(testDir, [selectedCandidate])
        CountingListener listener = new CountingListener()

        when:
        def resolver = new DefaultScriptFileResolver(listener)
        resolver.resolveScriptFile(testDir, "build")

        then:
        listener.notifiedFiles == expectedNotifiedFiles

        where:
        selectedCandidate        | expectedNotifiedFiles
        "build.gradle"           | ["build.gradle"]
        "build.gradle.kts"       | ["build.gradle", "build.gradle.kts"]
        "build.gradle.dcl"       | ["build.gradle", "build.gradle.kts", "build.gradle.dcl"]
    }

    static class CountingListener implements ScriptFileResolvedListener {
        private List<String> notifiedFileNames = []

        @Override
        void onScriptFileResolved(File scriptFile) {
            notifiedFileNames.add(scriptFile.getName())
        }

        int getCount() {
            return notifiedFileNames.size()
        }

        List<String> getNotifiedFiles() {
            return notifiedFileNames
        }
    }

    static List<File> createFiles(File dir, List<String> filenames) {
        return filenames.collect {
            def f = new File(dir, it)
            f.createNewFile()
            f
        }
    }

}
