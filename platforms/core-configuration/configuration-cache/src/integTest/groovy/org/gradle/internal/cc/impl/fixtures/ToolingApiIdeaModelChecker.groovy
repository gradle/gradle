/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.fixtures

import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkGradleProject
import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel
import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkProjectIdentifier

class ToolingApiIdeaModelChecker {

    static void checkIdeaProject(IdeaProject actual, IdeaProject expected) {
        checkModel(actual, expected, [
            { it.parent },
            { it.name },
            { it.description },
            { it.jdkName },
            { it.languageLevel.level },
            [{ it.children }, { a, e -> checkIdeaModule(a, e) }],
        ])
    }

    private static void checkIdeaModule(IdeaModule actualModule, IdeaModule expectedModule) {
        checkModel(actualModule, expectedModule, [
            { it.name },
            [{ it.projectIdentifier }, { a, e -> checkProjectIdentifier(a, e) }],
            [{ it.javaLanguageSettings }, { a, e -> checkLanguageSettings(a, e) }],
            { it.jdkName },
            [{ it.contentRoots }, { a, e -> checkContentRoot(a, e) }],
            [{ it.gradleProject }, { a, e -> checkGradleProject(a, e) }],
            { it.project.languageLevel.level }, // shallow check to avoid infinite recursion
            { it.compilerOutput.inheritOutputDirs },
            { it.compilerOutput.outputDir },
            { it.compilerOutput.testOutputDir },
            [{ it.dependencies }, { a, e -> checkDependency(a, e) }],
        ])
    }

    private static void checkContentRoot(IdeaContentRoot actual, IdeaContentRoot expected) {
        checkModel(actual, expected, [
            { it.rootDirectory },
            { it.excludeDirectories },
        ])
    }

    private static void checkDependency(IdeaDependency actual, IdeaDependency expected) {
        checkModel(actual, expected, [
            { it.scope.scope },
            { it.exported },
        ])

        if (expected instanceof IdeaModuleDependency) {
            checkModel(actual, expected, [
                { it.targetModuleName },
            ])
        }

        if (expected instanceof IdeaSingleEntryLibraryDependency) {
            checkModel(actual, expected, [
                { it.file },
                { it.source },
                { it.javadoc },
                { it.exported },
            ])
        }
    }

    private static void checkLanguageSettings(IdeaJavaLanguageSettings actual, IdeaJavaLanguageSettings expected) {
        checkModel(actual, expected, [
            { it.languageLevel },
            { it.targetBytecodeVersion },
            { it.jdk?.javaVersion },
            { it.jdk?.javaHome },
        ])
    }

}
