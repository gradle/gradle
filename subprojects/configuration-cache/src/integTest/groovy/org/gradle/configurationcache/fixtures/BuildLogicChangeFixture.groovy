/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import groovy.transform.Immutable
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.TestFile

class BuildLogicChangeFixture {

    static List<Spec> specs() {
        [Kind.values(), Language.values()].combinations().collect { Kind kind, Language language ->
            new Spec(language, kind)
        }
    }

    @Immutable
    static class Spec {
        Language language
        Kind kind

        @Override
        String toString() {
            "$language project ($kind)"
        }

        BuildLogicChangeFixture fixtureForProjectDir(TestFile projectDir) {
            new BuildLogicChangeFixture(projectDir, language, kind)
        }
    }

    enum Language {
        JAVA,
        GROOVY,
        KOTLIN

        String getFileExtension() {
            switch (this) {
                case JAVA:
                    return 'java'
                case GROOVY:
                    return 'groovy'
                case KOTLIN:
                    return 'kt'
            }
        }

        @Override
        String toString() {
            name().toLowerCase().capitalize()
        }
    }

    enum Kind {
        CHANGE_SOURCE,
        ADD_SOURCE,
        CHANGE_RESOURCE,
        ADD_RESOURCE

        @Override
        String toString() {
            name().toLowerCase().replace('_', ' ')
        }
    }

    static final String ORIGINAL_GREETING = 'Hello!'
    static final String CHANGED_GREETING = "G'day!"

    public final String pluginId = 'build-logic'
    public final String task = 'greet'
    public final TestFile projectDir
    public final Language language
    public final Kind kind
    public final TestFile buildFile

    BuildLogicChangeFixture(TestFile projectDir, Language language = Language.JAVA, Kind kind = Kind.CHANGE_SOURCE) {
        this.projectDir = projectDir
        this.language = language
        this.kind = kind
        this.buildFile = file("build.gradle.kts")
    }

    void setup() {
        buildFile << kotlinDslScriptForLanguage
        buildFile << """
            gradlePlugin {
                plugins {
                    register("$pluginId") {
                        id = "$pluginId"
                        implementationClass = "BuildLogicPlugin"
                    }
                }
            }
        """
        writeBuildLogicPlugin()
        writeGreetTask "GreetTask", ORIGINAL_GREETING
        switch (kind) {
            case Kind.CHANGE_RESOURCE:
                writeResource ""
                break
        }
    }

    void applyChange() {
        switch (kind) {
            case Kind.CHANGE_SOURCE:
                writeGreetTask "GreetTask", CHANGED_GREETING
                break
            case Kind.ADD_SOURCE:
                writeGreetTask "AnotherTask", CHANGED_GREETING
                break
            case Kind.CHANGE_RESOURCE:
            case Kind.ADD_RESOURCE:
                writeResource "42"
                break
        }
    }

    String getExpectedCacheInvalidationMessage() {
        "configuration cache cannot be reused because an input to task ':${projectDir.name}:$invalidatedTaskName' has changed."
    }

    String getInvalidatedTaskName() {
        switch (kind) {
            case Kind.CHANGE_RESOURCE:
            case Kind.ADD_RESOURCE:
                return 'processResources'
            default:
                return "compile$language"
        }
    }

    String getExpectedOutputBeforeChange() {
        ORIGINAL_GREETING
    }

    String getExpectedOutputAfterChange() {
        switch (kind) {
            case Kind.CHANGE_SOURCE:
                return CHANGED_GREETING
            default:
                return ORIGINAL_GREETING
        }
    }

    private String getKotlinDslScriptForLanguage() {
        """
            plugins {
                $languagePlugin
                `java-gradle-plugin`
            }

            ${language == Language.KOTLIN
            ? KotlinDslTestUtil.kotlinDslBuildSrcConfig
            : ''}
        """
    }

    private String getLanguagePlugin() {
        switch (language) {
            case Language.JAVA:
                return "`java-library`"
            case Language.GROOVY:
                return "groovy"
            case Language.KOTLIN:
                return "`kotlin-dsl-base`"
        }
    }

    private void writeBuildLogicPlugin() {
        switch (language) {
            case Language.JAVA:
            case Language.GROOVY:
                writeSourceFile language.fileExtension, "BuildLogicPlugin.${language.fileExtension}", """
                    public class BuildLogicPlugin implements ${Plugin.name}<${Project.name}> {
                        public void apply(${Project.name} project) {
                            project.getTasks().register("$task", GreetTask.class);
                        }
                    }
                """
                break
            case Language.KOTLIN:
                writeSourceFile "kotlin", "BuildLogicPlugin.kt", """
                    class BuildLogicPlugin : ${Plugin.name}<${Project.name}> {
                        override fun apply(project: ${Project.name}) {
                            project.tasks.register("$task", GreetTask::class.java);
                        }
                    }
                """
                break
        }
    }

    private void writeGreetTask(String className, String greeting) {
        switch (language) {
            case Language.GROOVY:
            case Language.JAVA:
                writeSourceFile language.fileExtension, "${className}.${language.fileExtension}", """
                    public class ${className} extends ${DefaultTask.name} {
                        @${TaskAction.name} void greet() { System.out.println("${greeting}"); }
                    }
                """
                break
            case Language.KOTLIN:
                writeSourceFile "kotlin", "${className}.kt", """
                    open class ${className} : ${DefaultTask.name}() {
                        @${TaskAction.name} fun greet() { println("${greeting}") }
                    }
                """
                break
        }
    }

    private void writeResource(String text) {
        writeSourceFile "resources", "resource.txt", text
    }

    private void writeSourceFile(String sourceSet, String fileName, String text) {
        file("src/main/" + sourceSet + "/" + fileName).text = text
    }

    private TestFile file(String path) {
        projectDir.file(path)
    }
}
