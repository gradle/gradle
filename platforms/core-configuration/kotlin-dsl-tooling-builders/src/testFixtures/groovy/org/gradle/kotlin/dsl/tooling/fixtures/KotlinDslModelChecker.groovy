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


import java.nio.file.Paths
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel

class KotlinDslModelChecker {

    static void checkScriptModelEditorReportsArePositioned(Map<File, KotlinDslScriptModel> scriptModels, String scriptFileName) {
        def expectedPath = Paths.get(scriptFileName)
        def script = scriptModels.keySet().find { it.toPath().endsWith(expectedPath) }
        assert script != null: "no script model found ending with '$scriptFileName'"
        assert scriptModels[script].editorReports.any { it.position != null }: "no positioned editor report on '$scriptFileName'"
    }

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
            { it.classPath },
            { it.sourcePath },
            { it.implicitImports },
            [{ it.editorReports }, { a, e -> checkEditorReport(a, e) }],
            // Stack-trace strings legitimately differ between IP and non-IP runs
            // (different call stacks during script evaluation). Compare only the
            // exception class + message line, which is stable across the two modes.
            { it.exceptions.collect { normalizeExceptionSummary(it) } },
        ])
    }

    private static String normalizeExceptionSummary(String exceptionString) {
        // Drop stack-frame lines (start with whitespace: "\tat ..." or "\t... N more").
        // This yields the exception class + message and any "Caused by:" headers,
        // which are stable across IP and non-IP runs.
        exceptionString.readLines()
            .findAll { !it.matches(/\s+.*/) }
            .collect { normalizeCompilerTempPath(it) }
            .join("\n")
    }

    // A compile error's ScriptCompilationException reports its 'location' in a throwaway file, not the real
    // script: the Kotlin DSL compiles generated "residual program" fragments written to a fresh
    // "gradle-kotlin-dsl-<random>" temp dir (see TemporaryScriptFiles.withTemporaryScriptFileFor). IP and
    // non-IP get different <random> values, so scrub it — otherwise it's the one field that breaks parity.
    private static String normalizeCompilerTempPath(String line) {
        line.replaceAll(/gradle-kotlin-dsl-\d+\.tmp/, "gradle-kotlin-dsl-<tmp>.tmp")
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
}
