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
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class InstantExecutionBuildSrcChangesIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    private static final String TASK_NAME = "greet"
    private static final String ORIGINAL_GREETING = "Hello!"
    private static final String CHANGED_GREETING = "G'day!"

    @Unroll
    def "invalidates cache upon change to buildSrc #language project (#change)"() {
        given:
        def instant = newInstantExecutionFixture()
        def fixture = new BuildSrcChangeFixture(testDirectory, language, change)
        fixture.setup()

        when:
        if (isKotlinBuildSrc) {
            problems.withDoNotFailOnProblems()
        }
        instantRun()

        then:
        outputContains ORIGINAL_GREETING

        when:
        fixture.applyChange()
        if (isKotlinBuildSrc) {
            problems.withDoNotFailOnProblems()
        }
        instantRun()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateStored()

        when:
        instantRun()

        then:
        outputContains fixture.expectedOutputAfterChange
        instant.assertStateLoaded()

        where:
        [language_, change_] << [BuildSrcLanguage.values(), BuildSrcChange.values()].combinations()
        language = language_ as BuildSrcLanguage
        change = change_ as BuildSrcChange

        isKotlinBuildSrc = language == BuildSrcLanguage.KOTLIN
    }

    private instantRun() {
        instantRun TASK_NAME
    }

    static class BuildSrcChangeFixture {

        private final TestFile projectDir
        private final BuildSrcLanguage language
        private final BuildSrcChange change

        BuildSrcChangeFixture(TestFile projectDir, BuildSrcLanguage language, BuildSrcChange change) {
            this.projectDir = projectDir
            this.language = language
            this.change = change
        }

        void setup() {
            file("build.gradle") << """
                task $TASK_NAME(type: GreetTask)
            """
            file("buildSrc/build.gradle.kts") << kotlinDslBuildSrcScript
            writeGreetTask "GreetTask", ORIGINAL_GREETING
            switch (change) {
                case BuildSrcChange.CHANGE_RESOURCE:
                    writeBuildSrcResource ""
                    break
            }
        }

        void applyChange() {
            switch (change) {
                case BuildSrcChange.CHANGE_SOURCE:
                    writeGreetTask "GreetTask", CHANGED_GREETING
                    break
                case BuildSrcChange.ADD_SOURCE:
                    writeGreetTask "AnotherTask", CHANGED_GREETING
                    break
                case BuildSrcChange.CHANGE_RESOURCE:
                case BuildSrcChange.ADD_RESOURCE:
                    writeBuildSrcResource "42"
                    break
            }
        }

        String getExpectedOutputAfterChange() {
            switch (change) {
                case BuildSrcChange.CHANGE_SOURCE:
                    return CHANGED_GREETING
                default:
                    return ORIGINAL_GREETING
            }
        }

        private String getKotlinDslBuildSrcScript() {
            switch (language) {
                case BuildSrcLanguage.JAVA:
                    return "plugins { `java-library` }"
                case BuildSrcLanguage.GROOVY:
                    return "plugins { groovy }"
                case BuildSrcLanguage.KOTLIN:
                    return KotlinDslTestUtil.kotlinDslBuildSrcScript
            }
        }

        private void writeGreetTask(String className, String greeting) {
            switch (language) {
                case BuildSrcLanguage.JAVA:
                    writeJavaTask(className, greeting)
                    break
                case BuildSrcLanguage.GROOVY:
                    writeGroovyTask(className, greeting)
                    break
                case BuildSrcLanguage.KOTLIN:
                    writeKotlinTask(className, greeting)
                    break
            }
        }

        private void writeJavaTask(String className, String greeting) {
            writeBuildSrcSource "java", "${className}.java", """
                public class $className extends ${DefaultTask.name} {
                    @${TaskAction.name} void greet() { System.out.println("$greeting"); }
                }
            """
        }

        private void writeGroovyTask(String className, String greeting) {
            writeBuildSrcSource "groovy", "${className}.groovy", """
                public class $className extends ${DefaultTask.name} {
                    @${TaskAction.name} void greet() { System.out.println("$greeting"); }
                }
            """
        }

        private void writeKotlinTask(String className, String greeting) {
            writeBuildSrcSource "kotlin", "${className}.kt", """
                open class $className : ${DefaultTask.name}() {
                    @${TaskAction.name} fun greet() { println("$greeting") }
                }
            """
        }

        private void writeBuildSrcResource(String text) {
            writeBuildSrcSource "resources", "resource.txt", text
        }

        private void writeBuildSrcSource(String sourceSet, String fileName, String text) {
            file("buildSrc/src/main/" + sourceSet + "/" + fileName).text = text
        }

        private TestFile file(String path) {
            projectDir.file(path)
        }
    }

    enum BuildSrcChange {
        CHANGE_SOURCE,
        ADD_SOURCE,
        CHANGE_RESOURCE,
        ADD_RESOURCE

        @Override
        String toString() {
            name().toLowerCase().replace('_', ' ')
        }
    }

    enum BuildSrcLanguage {
        JAVA,
        GROOVY,
        KOTLIN

        @Override
        String toString() {
            name().toLowerCase().capitalize()
        }
    }
}
