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
package org.gradle.performance.regression.inception

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.ScenarioContext
import org.gradle.util.GradleVersion
import org.junit.experimental.categories.Category

@Category(SlowPerformanceRegressionTest)
class BuildSrcApiChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        def targetVersion = "6.8-20200927220040+0000"
        runner.targetVersions = [targetVersion]
        runner.minimumBaseVersion = GradleVersion.version(targetVersion).baseVersion.version
    }

    def "buildSrc abi change"() {
        given:
        runner.tasksToRun = ['help']
        runner.runs = determineNumberOfRuns(runner.testProject)

        and:
        def changingClassFilePath = "buildSrc/src/main/groovy/ChangingClass.groovy"
        runner.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                @Override
                void beforeBuild(BuildContext context) {
                    writeFile(new File(invocationSettings.projectDir, changingClassFilePath), """
                        class ChangingClass {
                            void changingMethod${context.phase}${context.iteration}() {}
                        }
                    """)
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "buildSrc non-abi change"() {
        given:
        runner.tasksToRun = ['help']
        runner.runs = determineNumberOfRuns(runner.testProject)

        and:
        runner.addBuildMutator { settings ->
            def changingClassFilePath = new File(settings.projectDir, 'buildSrc/src/main/groovy/ChangingClass.groovy')
            new BuildMutator() {
                @Override
                void beforeScenario(ScenarioContext context) {
                    writeFile(changingClassFilePath, """
                        class ChangingClass {
                            void changingMethod${context.phase}${context.iteration}() {
                                System.out.println("Do the thing");
                            }
                        }
                    """)
                }

                @Override
                void beforeBuild(BuildContext context) {
                    writeFile(changingClassFilePath, """
                        class ChangingClass {
                            void changingMethod${context.phase}${context.iteration}() {
                                System.out.println("Do the other thing");
                            }
                        }
                    """)
                }
            }
        }
    }

    private static int determineNumberOfRuns(String testProject) {
        switch (testProject) {
            case 'mediumMonolithicJavaProject':
                return 40
            case 'largeJavaMultiProject':
                return 20
            case 'largeJavaMultiProjectKotlinDsl':
                return 10
            default:
                20
        }
    }

    private static void writeFile(File file, String content) {
        file.tap {
            parentFile.mkdirs()
            text = content
        }
    }
}
