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

package org.gradle.instantexecution

import groovy.transform.Immutable
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.junit.Assume.assumeFalse

class InstantExecutionIncludedBuildChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    def "invalidates cache upon change to included #fixtureSpec"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = fixtureSpec.fixtureForProjectDir(file('build-logic'))
        fixture.setup()
        settingsFile << """
            includeBuild 'build-logic'
        """
        buildFile << """
            plugins { id('${fixture.pluginId}') }
        """

        when:
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputBeforeChange
        instant.assertStateStored()

        when:
        fixture.applyChange()
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        instantRunLenient fixture.task

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        fixtureSpec << BuildLogicChangeFixture.specs()
    }

    @Unroll
    def "invalidates cache upon change to #inputName used by included build"() {

        assumeFalse(
            'property from gradle.properties is not available to included build',
            inputName == 'gradle.properties'
        )

        given:
        def instant = newInstantExecutionFixture()
        def fixture = new BuildLogicChangeFixture(file('build-logic'))
        fixture.setup()
        fixture.buildFile << """

            interface Params : ${ValueSourceParameters.name} {
                val value: Property<String>
            }

            abstract class IsCi : ${ValueSource.name}<String, Params> {
                override fun obtain(): String? = parameters.value.orNull
            }

            val ciProvider = providers.of(IsCi::class.java) {
                parameters.value.set(providers.systemProperty("test_is_ci").forUseAtConfigurationTime())
            }

            val isCi = ${inputExpression}.forUseAtConfigurationTime()
            tasks {
                named("jar") {
                    if (isCi.isPresent) {
                        doLast { println("ON CI") }
                    } else {
                        doLast { println("NOT CI") }
                    }
                }
            }
        """
        settingsFile << """
            includeBuild 'build-logic'
        """
        buildFile << """
            plugins { id('${fixture.pluginId}') }
        """

        when:
        instantRunLenient fixture.task

        then:
        output.count("NOT CI") == 1
        instant.assertStateStored()

        when:
        instantRunLenient fixture.task

        then: "included build doesn't build"
        output.count("CI") == 0
        instant.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            instantRunLenient fixture.task
        } else {
            instantRunLenient fixture.task, inputArgument
        }

        then:
        output.count("ON CI") == 1
        instant.assertStateStored()

        where:
        inputName             | inputExpression                          | inputArgument
        'custom value source' | 'ciProvider'                             | '-Dtest_is_ci=true'
        'system property'     | 'providers.systemProperty("test_is_ci")' | '-Dtest_is_ci=true'
        'Gradle property'     | 'providers.gradleProperty("test_is_ci")' | '-Ptest_is_ci=true'
        'gradle.properties'   | 'providers.gradleProperty("test_is_ci")' | ''
    }

    static class BuildLogicChangeFixture {

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

        static final String pluginId = 'build-logic'
        static final String task = 'greet'
        static final String originalGreeting = 'Hello!'
        static final String changedGreeting = "G'day!"

        private final TestFile projectDir
        private final Language language
        private final Kind kind
        private final TestFile buildFile

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
            writeGreetTask "GreetTask", originalGreeting
            switch (kind) {
                case Kind.CHANGE_RESOURCE:
                    writeResource ""
                    break
            }
        }

        void applyChange() {
            switch (kind) {
                case Kind.CHANGE_SOURCE:
                    writeGreetTask "GreetTask", changedGreeting
                    break
                case Kind.ADD_SOURCE:
                    writeGreetTask "AnotherTask", changedGreeting
                    break
                case Kind.CHANGE_RESOURCE:
                case Kind.ADD_RESOURCE:
                    writeResource "42"
                    break
            }
        }

        String getExpectedOutputBeforeChange() {
            originalGreeting
        }

        String getExpectedOutputAfterChange() {
            switch (kind) {
                case Kind.CHANGE_SOURCE:
                    return changedGreeting
                default:
                    return originalGreeting
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
}
