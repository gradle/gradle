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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class DefaultScriptFileResolverTest extends AbstractIntegrationSpec {

    def "when multiple build scripts are present, the resolved will report a warning"(String acceptedScript, List<String> ignoredScripts) {
        given:
        enableProblemsApiCheck()
        def acceptedFile = file(acceptedScript)
        acceptedFile.touch()
        def ignoredFiles = ignoredScripts.collect { file(it) }
        ignoredFiles.each { it.touch() }

        when:
        succeeds("help")

        then:
        def problem = receivedProblem
        problem.fqid == "scripts:multiple-candidates"
        problem.contextualLabel == "Multiple script candidates were found in directory '${testDirectory.absolutePath}'"
        problem.details == expectedMessage(testDirectory, acceptedFile, ignoredFiles)

        where:
        [acceptedScript, ignoredScripts] << [
            ["build.gradle", ['build.gradle.kts', 'build.gradle.dcl']],
            ["build.gradle", ['build.gradle.dcl', 'build.gradle.kts']],
            ["build.gradle.kts", ['build.gradle.dcl']],
        ]
    }

    private def expectedMessage(TestFile rootDir, TestFile acceptedPath, List<TestFile> ignoredPaths) {
        // This method needs to keep a certain order, as DefaultScriptFileResolver will receive the extensions from `ScriptFileUtil.getValidExtensions()` in a particular order.
        def lines = [" - Selected candidate: '${acceptedPath.absolutePath}'"]
        ignoredPaths.findAll { it.name.endsWith(".kts") }.each {
            lines << " - Ignored candidate: '${it.absolutePath}'"
        }
        ignoredPaths.findAll { it.name.endsWith(".dcl") }.each {
            lines << " - Ignored candidate: '${it.absolutePath}'"
        }

        return lines.join(System.lineSeparator())
    }

}
