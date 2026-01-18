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
     * Please review the tests in this file, and add the new extension to the list below.
     */
    def "list of extensions are what we expect"() {
        ScriptingLanguages.all().collect { it.extension } == [
            ".gradle",
            ".gradle.kts",
            ".gradle.dcl"
        ]
    }

    def "when no script file is found, resolution result should be empty"() {
        given:
        createFiles(testDir, ["a.gradle", "a.gradle.kts", "a.gradle.dcl"])

        when:
        def resolver = new DefaultScriptFileResolver()
        def result = resolver.resolveScriptFile(testDir, "build")

        then:
        result.selectedCandidate == null
        result.ignoredCandidates.isEmpty()
    }


    def "resolution finds and notifies the correct script file"() {
        given:
        createFiles(testDir, createFiles)
        def listener = new ScriptFileResolutionRecorder()

        when:
        def resolver = new DefaultScriptFileResolver(listener)
        def result = resolver.resolveScriptFile(testDir, "a")

        then:
        result.selectedCandidate?.name == expectedSelectedCandidate
        listener.notifiedFiles == expectedNotifiedFiles

        where:
        createFiles                                  | expectedSelectedCandidate | expectedNotifiedFiles
        []                                           | null                      | ["a.gradle", "a.gradle.kts", "a.gradle.dcl"]
        ["a.gradle"]                                 | "a.gradle"                | ["a.gradle"]
        ["a.gradle.kts"]                             | "a.gradle.kts"            | ["a.gradle", "a.gradle.kts"]
        ["a.gradle.dcl"]                             | "a.gradle.dcl"            | ["a.gradle", "a.gradle.kts", "a.gradle.dcl"]
        ["a.gradle", "a.gradle.kts"]                 | "a.gradle"                | ["a.gradle"]
        ["a.gradle", "a.gradle.dcl"]                 | "a.gradle"                | ["a.gradle"]
        ["a.gradle.kts", "a.gradle.dcl"]             | "a.gradle.kts"            | ["a.gradle", "a.gradle.kts"]
        ["a.gradle", "a.gradle.kts", "a.gradle.dcl"] | "a.gradle"                | ["a.gradle"]
    }

    /**
     * A simple listener implementation that counts the number of times it was notified of a resolved script file.
     */
    static class ScriptFileResolutionRecorder implements ScriptFileResolverListeners {
        private List<String> notifiedFileNames = []

        @Override
        void addListener(ScriptFileResolvedListener scriptFileResolvedListener) {
            // No-op
        }

        @Override
        void removeListener(ScriptFileResolvedListener scriptFileResolvedListener) {
            // No-op
        }

        @Override
        void onScriptFileResolved(File scriptFile) {
            notifiedFileNames.add(scriptFile.getName())
        }

        List<String> getNotifiedFiles() {
            return notifiedFileNames
        }
    }

    /**
     * Generate a list of filenames with all known scripting language extensions.
     *
     * @param basename the base name of the file, e.g `build`
     * @return a list of filenames with all known extensions, e.g. `['build.gradle', 'build.gradle.kts', 'build.gradle.dcl']`
     */
    static List<String> withExtensions(String basename) {
        return ScriptingLanguages.all().collect {
            "${basename}${it.extension}".toString()
        }
    }

    /**
     * Create files with the given filenames in the given directory.
     * @param dir the directory in which to create the files
     * @param filenames the names of the files to create
     * @return the list of created files
     */
    static List<File> createFiles(File dir, List<String> filenames) {
        return filenames.collect {
            def f = new File(dir, it)
            f.createNewFile()
            f
        }
    }

}
