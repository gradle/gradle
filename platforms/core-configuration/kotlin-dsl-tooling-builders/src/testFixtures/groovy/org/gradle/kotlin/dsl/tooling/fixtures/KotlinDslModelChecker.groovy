/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.fixtures


import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import spock.lang.Issue

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel

class KotlinDslModelChecker {

    static void checkBuildTreeScriptsModels(Map<String, KotlinDslScriptsModel> actual, Map<String, KotlinDslScriptsModel> expected) {
        assert actual.keySet() == expected.keySet()
        actual.each { build, actualModel ->
            checkKotlinDslScriptsModel(actualModel, expected[build])
        }
    }

    static void checkKotlinDslScriptsModel(KotlinDslScriptsModel actual, KotlinDslScriptsModel expected) {
        checkModel(actual, expected, [
            [{ it.scriptModels }, { a, e -> checkKotlinDslScriptModel(a, e) }]
        ])
    }

    static void checkKotlinDslScriptModel(KotlinDslScriptModel actual, KotlinDslScriptModel expected) {
        checkModel(actual, expected, [
            { withNormalizedAccessorHash(it.classPath) },
            { withNormalizedAccessorHash(it.sourcePath) },
            { it.implicitImports },
            // TODO:isolated support editor reports
//            [{ it.editorReports }, { a, e -> checkEditorReport(a, e) }],
            { it.exceptions },
        ])
    }

    private static void checkEditorReport(actual, expected) {
        assert expected instanceof EditorReport
        assert actual instanceof EditorReport

        checkModel(actual, expected, [
            { it.severity },
            { it.message },
            [{ it.position }, [
                { it.line },
                { it.column },
            ]]
        ])
    }

    // replaces `kotlin-dsl/accessors/*hash*/` with `kotlin-dsl/accessors/<hash>`
    @Issue("https://github.com/gradle/gradle/issues/37719")
    private static List<File> withNormalizedAccessorHash(List<File> paths) {
        final String accessorPathPrefix = "kotlin-dsl" + File.separator + "accessors" + File.separator
        paths.collect { File f ->
            int idx = f.path.indexOf(accessorPathPrefix)
            if (idx < 0) {
                return f
            }
            int hashEnd = f.path.indexOf(File.separator, idx + accessorPathPrefix.length())
            return new File(f.path.substring(0, idx + accessorPathPrefix.length()) + "<hash>" + f.path.substring(hashEnd))
        }
    }
}
