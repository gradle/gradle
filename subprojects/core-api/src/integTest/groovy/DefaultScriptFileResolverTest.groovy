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
import org.gradle.internal.scripts.ScriptFileUtil
import org.gradle.test.fixtures.file.TestFile

import java.util.stream.Collectors

class DefaultScriptFileResolverTest extends AbstractIntegrationSpec {

    def "when multiple build scripts are present, the resolved will report a warning"(String acceptedScript, List<String> ignoredScripts) {
        given:
        def acceptedFile = file(acceptedScript)
        acceptedFile.touch()
        def ignoredFiles = ignoredScripts.collect { file(it) }
        ignoredFiles.each { it.touch() }

        when:
        succeeds("help")

        then:
        outputContains(expectedMessage(testDirectory, acceptedFile, ignoredFiles))
        where:
        [acceptedScript, ignoredScripts] << createCombinations(ScriptFileUtil.getValidExtensions())
    }

    private static List<List<Object>> createCombinations(String... extensions) {
        (0..<extensions.length - 1).collectMany { i ->
            def accepted = "build${extensions[i]}"
            def ignored = extensions[i + 1..<extensions.length].collect { "build${it}" }
            ignored.permutations().collect { ignoredList -> [accepted, ignoredList] }
        }
    }

    private def expectedMessage(TestFile rootDir, TestFile acceptedPath, List<TestFile> ignoredPaths) {
        // This method needs to keep a certain order, as DefaultScriptFileResolver will receive the extensions from `ScriptFileUtil.getValidExtensions()` in a particular order.
        def reorderedIgnoredPaths = []
        ignoredPaths.findAll { it.name.endsWith(".kts") }.each {
            reorderedIgnoredPaths.add("'${it.name}'")
        }
        ignoredPaths.findAll { it.name.endsWith(".dcl") }.each {
            reorderedIgnoredPaths.add("'${it.name}'")
        }

        def ignoredPathsList = reorderedIgnoredPaths.stream().collect(Collectors.joining(", "))
        return "Multiple build files were found in directory '${rootDir.absolutePath}'. Using '${acceptedPath.name}', and ignoring ${ignoredPathsList}"
    }

}
