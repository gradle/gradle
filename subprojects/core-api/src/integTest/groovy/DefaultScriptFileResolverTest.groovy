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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.scripts.ScriptingLanguages

class DefaultScriptFileResolverTest extends AbstractIntegrationSpec {

    /**
     * Tests in this class expect a well known, invariable set of script languages.
     * If this tests breaks, it means that the set of available script languages has changed and the tests need to be updated.
     */
    def "script languages are the expected ones"() {
        def extensions = ScriptingLanguages.all().collect {
            it.extension
        }.toSet()

        assert extensions == ['gradle', 'gradle.kts', 'gradle.dcl'] as Set
    }

    def "when multiple build scripts are present, the resolved will report a warning"() {

        given:
        buildFiles.each {
            file(it).touch()
        }

        when:
        succeeds("help")

        then:
        if (messageDetails != null) {
            outputContains("Multiple build files were found in directory '${testDirectory.absolutePath}': ${messageDetails}.")
        } else {
            outputDoesNotContain("Multiple build files were found in directory")
        }

        where:
        [buildFiles, messageDetails] << [
            [["build.gradle"], null],
            [["build.gradle", "build.gradle.kts"], "using 'build.gradle', and ignoring 'build.gradle.kts'"],
            [["build.gradle", "build.gradle.dcl"], "using 'build.gradle', and ignoring 'build.gradle.dcl'"],
            [["build.gradle.kts", "build.gradle.dcl"], "using 'build.gradle.kts', and ignoring 'build.gradle.dcl'"],
            [["build.gradle", "build.gradle.kts", "build.gradle.dcl"], "using 'build.gradle', and ignoring 'build.gradle.kts', 'build.gradle.dcl'"]
        ]
    }

}
