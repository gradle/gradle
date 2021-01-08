/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping

import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest

abstract class AbstractConsoleCompositeBuildGroupedTaskFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    private static final String PROJECT_A_NAME = 'projectA'
    private static final String PROJECT_B_NAME = 'projectB'
    private static final String HELLO_WORLD_MESSAGE = 'Hello world'
    private static final String BYE_WORLD_MESSAGE = 'Bye world'

    def setup() {
        file("$PROJECT_A_NAME/build.gradle") << javaProject()
        file("$PROJECT_A_NAME/build.gradle") <<
            """
                task helloWorld {
                    doLast {
                        logger.quiet 'Hello world'
                    }
                }

                compileJava.dependsOn helloWorld
            """
        file("$PROJECT_B_NAME/build.gradle") << javaProject()
        file("$PROJECT_B_NAME/build.gradle") <<
            """
                dependencies {
                    implementation 'org.gradle:projectA:1.0'
                }

                task byeWorld {
                    doLast {
                        logger.quiet 'Bye world'
                    }
                }

                compileJava.dependsOn byeWorld
            """
        file("$PROJECT_A_NAME/settings.gradle") << """
            rootProject.name = '$PROJECT_A_NAME'
        """
        file("$PROJECT_B_NAME/settings.gradle") << """
            rootProject.name = '$PROJECT_B_NAME'
            includeBuild '${file(PROJECT_A_NAME).toURI()}'
        """
        file("$PROJECT_A_NAME/src/main/java/MyClass.java") << javaSourceFile()
        file("$PROJECT_B_NAME/src/main/java/MyClass.java") << javaSourceFile()
    }

    def "can group task output in composite build"() {
        when:
        def result = executer.inDirectory(file(PROJECT_B_NAME)).withTasks('compileJava').run()

        then:
        result.groupedOutput.task(":$PROJECT_A_NAME:helloWorld").output == HELLO_WORLD_MESSAGE
        result.groupedOutput.task(':byeWorld').output == BYE_WORLD_MESSAGE
    }

    def "does not execute task actions when dry run specified on composite build"() {
        when:
        def result = executer.inDirectory(file(PROJECT_B_NAME)).withArgument("--dry-run").withTasks('compileJava').run()

        then:
        def strippedGroupedTaskOutput = result.groupedOutput.strippedOutput
        strippedGroupedTaskOutput.contains(':byeWorld SKIPPED')
        strippedGroupedTaskOutput.contains(':compileJava SKIPPED')
    }

    static String javaProject() {
        """
            apply plugin: 'java'

            group = 'org.gradle'
            version = '1.0'
        """
    }

    static String javaSourceFile() {
        """
            public class MyClass {}
        """
    }
}
