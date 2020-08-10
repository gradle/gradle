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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.junit.Assume.assumeFalse

class InstantExecutionIncludedBuildChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    @Unroll
    def "invalidates cache upon change to included #language project (#change)"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = new BuildLogicChangeFixture(file('build-logic'), language, change)
        fixture.setup()

        settingsFile << """
            includeBuild 'build-logic'
        """
        buildFile << """
            plugins { id('${BuildLogicChangeFixture.PLUGIN_ID}') }
        """
        when:
        instantRunLenient()

        then:
        outputContains fixture.expectedOutputBeforeChange

        when:
        fixture.applyChange()
        instantRunLenient()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        instantRunLenient()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        [language_, change_] << [BuildLogicLanguage.values(), BuildLogicChange.values()].combinations()
        language = language_ as BuildLogicLanguage
        change = change_ as BuildLogicChange
    }

    private void instantRunLenient() {
        instantRunLenient BuildLogicChangeFixture.TASK_NAME
    }

    @Unroll
    def "invalidates cache upon change to #inputName used by included build"() {

        assumeFalse(
            'property from gradle.properties is not available to included build',
            inputName == 'gradle.properties'
        )

        given:
        def instant = newInstantExecutionFixture()
        def fixture = new BuildLogicChangeFixture(file('build-logic'), BuildLogicLanguage.JAVA, BuildLogicChange.CHANGE_SOURCE)
        file("build-logic/build.gradle.kts") << """
            import org.gradle.api.provider.*
        """
        fixture.setup()
        file("build-logic/build.gradle.kts") << """

            interface Params: ValueSourceParameters {
                val value: Property<String>
            }

            abstract class IsCi : ValueSource<String, Params> {
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
            plugins { id('${BuildLogicChangeFixture.PLUGIN_ID}') }
        """

        when:
        instantRunLenient BuildLogicChangeFixture.TASK_NAME

        then:
        output.count("NOT CI") == 1
        instant.assertStateStored()

        when:
        instantRunLenient BuildLogicChangeFixture.TASK_NAME

        then: "included build doesn't build"
        output.count("CI") == 0
        instant.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            instantRunLenient BuildLogicChangeFixture.TASK_NAME
        } else {
            instantRunLenient BuildLogicChangeFixture.TASK_NAME, inputArgument
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

        static final String PLUGIN_ID = 'build-logic'
        static final String TASK_NAME = 'greet'
        static final String ORIGINAL_GREETING = 'Hello!'
        static final String CHANGED_GREETING = "G'day!"

        private final TestFile projectDir
        private final BuildLogicLanguage language
        private final BuildLogicChange change

        BuildLogicChangeFixture(TestFile projectDir, BuildLogicLanguage language, BuildLogicChange change) {
            this.projectDir = projectDir
            this.language = language
            this.change = change
        }

        void setup() {
            final buildFile = file("build.gradle.kts")
            buildFile << kotlinDslBuildSrcScript
            buildFile << """
                gradlePlugin {
                    plugins {
                        register("$PLUGIN_ID") {
                            id = "$PLUGIN_ID"
                            implementationClass = "BuildLogicPlugin"
                        }
                    }
                }
            """
            writeBuildLogicPlugin()
            writeGreetTask "GreetTask", ORIGINAL_GREETING
            switch (change) {
                case BuildLogicChange.CHANGE_RESOURCE:
                    writeResource ""
                    break
            }
        }

        void applyChange() {
            switch (change) {
                case BuildLogicChange.CHANGE_SOURCE:
                    writeGreetTask "GreetTask", CHANGED_GREETING
                    break
                case BuildLogicChange.ADD_SOURCE:
                    writeGreetTask "AnotherTask", CHANGED_GREETING
                    break
                case BuildLogicChange.CHANGE_RESOURCE:
                case BuildLogicChange.ADD_RESOURCE:
                    writeResource "42"
                    break
            }
        }

        String getExpectedOutputBeforeChange() {
            ORIGINAL_GREETING
        }

        String getExpectedOutputAfterChange() {
            switch (change) {
                case BuildLogicChange.CHANGE_SOURCE:
                    return CHANGED_GREETING
                default:
                    return ORIGINAL_GREETING
            }
        }

        private String getKotlinDslBuildSrcScript() {
            """
                plugins {
                    $languagePlugin
                    `java-gradle-plugin`
                }

                ${language == BuildLogicLanguage.KOTLIN
                ? KotlinDslTestUtil.kotlinDslBuildSrcConfig
                : ''}
            """
        }

        private String getLanguagePlugin() {
            switch (language) {
                case BuildLogicLanguage.JAVA:
                    return "`java-library`"
                case BuildLogicLanguage.GROOVY:
                    return "groovy"
                case BuildLogicLanguage.KOTLIN:
                    return "`kotlin-dsl-base`"
            }
        }

        private void writeBuildLogicPlugin() {
            switch (language) {
                case BuildLogicLanguage.JAVA:
                case BuildLogicLanguage.GROOVY:
                    writeSourceFile language.fileExtension, "BuildLogicPlugin.${language.fileExtension}", """
                        public class BuildLogicPlugin implements ${Plugin.name}<${Project.name}> {
                            public void apply(${Project.name} project) {
                                project.getTasks().register("$TASK_NAME", GreetTask.class);
                            }
                        }
                    """
                    break
                case BuildLogicLanguage.KOTLIN:
                    writeSourceFile "kotlin", "BuildLogicPlugin.kt", """
                        class BuildLogicPlugin : ${Plugin.name}<${Project.name}> {
                            override fun apply(project: ${Project.name}) {
                                project.tasks.register("$TASK_NAME", GreetTask::class.java);
                            }
                        }
                    """
                    break
            }
        }

        private void writeGreetTask(String className, String greeting) {
            switch (language) {
                case BuildLogicLanguage.JAVA:
                    writeJavaTask(className, greeting)
                    break
                case BuildLogicLanguage.GROOVY:
                    writeGroovyTask(className, greeting)
                    break
                case BuildLogicLanguage.KOTLIN:
                    writeKotlinTask(className, greeting)
                    break
            }
        }

        private void writeJavaTask(String className, String greeting) {
            writeSourceFile "java", "${className}.java", """
                public class $className extends ${DefaultTask.name} {
                    @${TaskAction.name} void greet() { System.out.println("$greeting"); }
                }
            """
        }

        private void writeGroovyTask(String className, String greeting) {
            writeSourceFile "groovy", "${className}.groovy", """
                public class $className extends ${DefaultTask.name} {
                    @${TaskAction.name} void greet() { System.out.println("$greeting"); }
                }
            """
        }

        private void writeKotlinTask(String className, String greeting) {
            writeSourceFile "kotlin", "${className}.kt", """
                open class $className : ${DefaultTask.name}() {
                    @${TaskAction.name} fun greet() { println("$greeting") }
                }
            """
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

    enum BuildLogicChange {
        CHANGE_SOURCE,
        ADD_SOURCE,
        CHANGE_RESOURCE,
        ADD_RESOURCE

        @Override
        String toString() {
            name().toLowerCase().replace('_', ' ')
        }
    }

    enum BuildLogicLanguage {
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
}
