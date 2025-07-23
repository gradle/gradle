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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiBackedGradleExecuter
import org.gradle.integtests.tooling.fixture.ToolingApiSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.kotlin.dsl.KotlinDslModelsParameters
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel

class FailedSyncIntegrationTest extends AbstractIntegrationSpec implements ToolingApiSpec {

    def setup() {
        executer.withArguments(KotlinDslModelsParameters.CLASSPATH_MODE_SYSTEM_PROPERTY_DECLARATION)
    }

    def "basic build - broken main settings file"() {
        given:
        settingsKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":"]
        // TODO: validate script models
    }

    def "basic build - broken root build file"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
        """
        buildKotlinFile << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":"]
        // TODO: validate script models
    }

    def "basic project w/ included build - broken build file in included build"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            rootProject.name = "included"
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
        // TODO: validate script models
    }

    def "basic build w/ included build - broken settings file in included build"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            // nothing interesting
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
        // TODO: validate script models
    }

    def "basic build w/ included build - broken settings and build file in included build"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            includeBuild("included")
        """

        def included = testDirectory.createDir("included")
        included.file("settings.gradle.kts") << """
            boom !!!
        """
        included.file("build.gradle.kts") << """
            blow up !!!
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":included"]
        // TODO: validate script models
    }

    def "multi project build - broken build file in one subproject"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a")
            include("b")
            include("c")
        """

        def buildFileA = testDirectory.createDir("a").file("build.gradle.kts")
        buildFileA << """
            // nothing interesting
        """
        def buildFileB = testDirectory.createDir("b").file("build.gradle.kts")
        buildFileB << """
            blow up !!!
        """
        def buildFileC = testDirectory.createDir("c").file("build.gradle.kts")
        buildFileC << """
            // nothing interesting
        """

        when:
        MyCustomModel model = runBuildAction(new CustomModelAction())

        then:
        model.paths == [":", ":a", ":b", ":c"]
        model.scriptModels.size() == 4
        validScriptModelForFile(model.scriptModels, settingsKotlinFile)
        validScriptModelForFile(model.scriptModels, buildFileA)
        validScriptModelForFile(model.scriptModels, buildFileB, "Script compilation error")
        validScriptModelForFile(model.scriptModels, buildFileC)
    }


    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    private static boolean validScriptModelForFile(Map<File, KotlinDslScriptModel> scriptModelsForFiles, TestFile file, String expectedError = null) {
        def scriptModel = scriptModelsForFiles.get(file)
        scriptModel != null && validClassPath(scriptModel) && validSourcePath(scriptModel) && validImplicitImports(scriptModel) && validExceptions(scriptModel, expectedError)
    }

    private static boolean validClassPath(KotlinDslScriptModel scriptModel) {
        List<File> classPath = scriptModel.classPath
        containsJar(classPath, "gradle-api") && containsJar(classPath, "gradle-kotlin-dsl") && containsJar(classPath, "kotlin-stdlib")
    }

    private static boolean validSourcePath(KotlinDslScriptModel scriptModel) {
        def sourcePath = scriptModel.sourcePath
        containsJar(sourcePath, "gradle-kotlin-dsl") && containsKotlinDslAccessorsDir(sourcePath)
    }

    private static boolean validImplicitImports(KotlinDslScriptModel scriptModel) {
        def implicitImports = scriptModel.implicitImports
        implicitImports.contains("org.gradle.api.Project") && implicitImports.contains("org.gradle.api.artifacts.dsl.Dependencies") && implicitImports.contains("org.gradle.api.file.RegularFile")
    }

    private static boolean validExceptions(KotlinDslScriptModel scriptModel, String expectedError) {
        def exceptions = scriptModel.exceptions
        if (expectedError == null) {
            exceptions.size() == 0
        } else {
            exceptions.size() == 1 && exceptions[0].contains(expectedError)
        }
    }

    private static boolean containsJar(List<File> files, String jarBaseName) {
        files.any { file -> file.name.startsWith(jarBaseName + "-") && file.name.endsWith(".jar") }
    }

    private static boolean containsKotlinDslAccessorsDir(List<File> files) {
        files.any { file -> file.isDirectory() && file.absolutePath.matches(".*/kotlin-dsl/accessors/.*/sources") }
    }
}
