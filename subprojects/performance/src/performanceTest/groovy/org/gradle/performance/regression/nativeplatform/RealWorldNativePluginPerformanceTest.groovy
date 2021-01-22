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

package org.gradle.performance.regression.nativeplatform

import org.apache.commons.io.FileUtils
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import spock.lang.Unroll

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["nativeMonolithic", "nativeMonolithicOverlapping"])
)
class RealWorldNativePluginPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.targetVersions = ["7.0-20210122131800+0000"]
        runner.minimumBaseVersion = "4.0"
    }

    @Unroll
    def "build with #parallelWorkers parallel workers"() {
        given:
        runner.tasksToRun = ['build']
        runner.warmUpRuns = 5
        runner.runs = 10

        runner.args += ["-Dorg.gradle.parallel=${parallelWorkers ? true : false}"]
        if (parallelWorkers) {
            runner.args += ["--max-workers=$parallelWorkers".toString()]
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        parallelWorkers << [0, 12]
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["mediumNativeMonolithic"], iterationMatcher = ".*(header|source) file.*"),
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["smallNativeMonolithic"], iterationMatcher = ".*build file.*")
    ])
    @Unroll
    def "build with #changeType file change"() {
        given:
        runner.tasksToRun = ['build']
        runner.warmUpRuns = 39
        runner.runs = 40

        def changedFile = getFileToChange(changeType)
        def changeClosure = getChangeClosure(changeType)
        runner.addBuildMutator { invocationSettings ->
            new BuildMutator() {
                String originalContent
                File originalContentFor

                @Override
                void beforeBuild(BuildContext context) {
                    File file = new File(invocationSettings.projectDir, changedFile)
                    if (originalContentFor != file) {
                        assert file.exists()
                        def backupFile = new File(file.parentFile, file.name + "~")
                        if (backupFile.exists()) {
                            originalContent = backupFile.text
                            file.text = originalContent
                        } else {
                            originalContent = file.text
                            FileUtils.copyFile(file, backupFile)
                        }
                        originalContentFor = file
                    }
                    if (context.iteration % 2 == 0) {
                        println "Changing $file"
                        // do change
                        changeClosure(file, originalContent)
                    } else if (context.iteration > 2) {
                        println "Reverting $file"
                        file.text = originalContent
                    }
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        changeType << ['header', 'source', 'build']
    }

    static String getFileToChange(String changeType) {
        switch (changeType) {
            case 'source':
                return 'modules/project5/src/src100_c.c'
            case 'header':
                return 'modules/project1/src/src50_h.h'
            case 'build':
                return 'common.gradle'
            default:
                throw new IllegalArgumentException("Unknown change type ${changeType}")
        }
    }

    Closure getChangeClosure(String changeType) {
        switch (changeType) {
            case 'source':
                return this.&changeCSource
            case 'header':
                return this.&changeHeader
            case 'build':
                return this.&changeArgs
            default:
                throw new IllegalArgumentException("Unknown change type ${changeType}")
        }
    }

    void changeCSource(File file, String originalContent) {
        file.text = originalContent + """\nint C_function_added_in_test () {
                    |  printf("Hello world!");
                    |  return 0;
                    |}\n""".stripMargin()
    }

    void changeHeader(File file, String originalContent) {
        file.text = originalContent.replaceFirst(~/#endif/, '#define HELLO_WORLD "Hello world!"\n#endif')
    }

    void changeArgs(File file, String originalContent) {
        file.text = originalContent.
            replaceFirst(~/cCompiler.define "SOMETHING7=0"/, 'cCompiler.define "SOMETHING_NEW=0"').
            replaceFirst(~/cppCompiler.define "SOMETHING7=0"/, 'cppCompiler.define "SOMETHING_NEW=0"')
    }
}
