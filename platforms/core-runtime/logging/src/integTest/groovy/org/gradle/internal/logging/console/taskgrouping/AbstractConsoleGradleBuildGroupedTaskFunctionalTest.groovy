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

abstract class AbstractConsoleGradleBuildGroupedTaskFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {

    private static final String HELLO_WORLD_MESSAGE = 'Hello world'
    private static final String IMPORTANT_MESSAGE = 'Something important needs to happen'
    private static final String BYE_WORLD_MESSAGE = 'Bye world'
    private static final String AGGREGATE_TASK_NAME = 'all'

    def "can group task output from external build invoked executed by GradleBuild in same directory"() {
        given:
        def externalBuildScriptPath = 'other.gradle'
        buildFile << mainBuildScript("buildFile = '$externalBuildScriptPath'")
        file(externalBuildScriptPath) << externalBuildScript()

        when:
        executer.expectDocumentedDeprecationWarning("The GradleBuild.buildFile property has been deprecated. This is scheduled to be removed in Gradle 9.0. Setting custom build file to select the root of the nested build has been deprecated. Please use the dir property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#configuring_custom_build_layout")
        executer.expectDocumentedDeprecationWarning("Specifying custom build file location has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#configuring_custom_build_layout")
        succeeds(AGGREGATE_TASK_NAME)

        then:
        result.groupedOutput.task(':helloWorld').output == HELLO_WORLD_MESSAGE
        result.groupedOutput.task(":${testDirectory.name}:important").output == IMPORTANT_MESSAGE
        result.groupedOutput.task(':byeWorld').output == BYE_WORLD_MESSAGE
    }

    def "can group task output from external build invoked executed by GradleBuild in different directory"() {
        given:
        def externalBuildPath = 'external'
        buildFile << mainBuildScript("dir = '$externalBuildPath'")
        file(externalBuildPath).file('settings.gradle') << "rootProject.name = 'external'"
        file(externalBuildPath).file('build.gradle') << externalBuildScript()

        when:
        succeeds(AGGREGATE_TASK_NAME)

        then:
        result.groupedOutput.task(':helloWorld').output == HELLO_WORLD_MESSAGE
        result.groupedOutput.task(":external:important").output == IMPORTANT_MESSAGE
        result.groupedOutput.task(':byeWorld').output == BYE_WORLD_MESSAGE
    }

    static String mainBuildScript(String externalBuildConfig) {
        """
            task helloWorld {
                doLast {
                    logger.quiet '$HELLO_WORLD_MESSAGE'
                }
            }

            task otherBuild(type: GradleBuild) {
                mustRunAfter helloWorld
                $externalBuildConfig
                tasks = ['important']
            }

            task byeWorld {
                mustRunAfter otherBuild

                doLast {
                    logger.quiet '$BYE_WORLD_MESSAGE'
                }
            }

            task $AGGREGATE_TASK_NAME {
                dependsOn helloWorld, otherBuild, byeWorld
            }
        """
    }

    static String externalBuildScript() {
        """
            task important {
                doLast {
                    logger.quiet '$IMPORTANT_MESSAGE'
                }
            }
        """
    }
}
