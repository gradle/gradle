/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

@IntegrationTestTimeout(600)
class BuildSourceBuilderIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-2032")
    def "can simultaneously run gradle on projects with buildSrc"() {
        given:
        def buildSrcDir = file("buildSrc").createDir()
        writeSharedClassFile(buildSrcDir)
        file('buildSrc/build.gradle').text = '''
            tasks.all {
                doFirst {
                    println "${name} started at ${new Date().time}"
                }
                doLast {
                    println "${name} finished at ${new Date().time}"
                }
            }
        '''
        buildFile.text = """
        import org.gradle.integtest.test.BuildSrcTask

        int MAX_LOOP_COUNT = java.util.concurrent.TimeUnit.MINUTES.toMillis(5) / 10
        task blocking(type:BuildSrcTask) {
            doLast {
                file("run1washere.lock").createNewFile()
                
                int count = 0
                while(!file("run2washere.lock").exists() && count++ < MAX_LOOP_COUNT){
                    sleep 10
                }
            }
        }

        task releasing(type:BuildSrcTask) {
            doLast {
                int count = 0
                while(!file("run1washere.lock").exists() && count++ < MAX_LOOP_COUNT){
                    sleep 10
                }
                file("run2washere.lock").createNewFile()
            }
        }
        """
        when:
        def runBlockingHandle = executer.withTasks("blocking").start()
        def runReleaseHandle = executer.withTasks("releasing").start()
        and:
        def releaseResult = runReleaseHandle.waitForFinish()
        def blockingResult = runBlockingHandle.waitForFinish()
        then:
        blockingResult.assertTasksExecuted(":blocking")
        releaseResult.assertTasksExecuted(":releasing")

        def blockingTaskTimes = finishedTaskTimes(blockingResult.output)
        def releasingTaskTimes = finishedTaskTimes(releaseResult.output)

        def blockingBuildSrcBuiltFirst = blockingTaskTimes.values().min() < releasingTaskTimes.values().min()
        def (firstBuildResult, secondBuildResult) = blockingBuildSrcBuiltFirst ? [blockingResult, releaseResult] : [releaseResult, blockingResult]

        def lastTaskTimeFromFirstBuildSrcBuild = finishedTaskTimes(firstBuildResult.output).values().max()
        def firstTaskTimeFromSecondBuildSrcBuild = startedTaskTimes(secondBuildResult.output).values().min()

        lastTaskTimeFromFirstBuildSrcBuild < firstTaskTimeFromSecondBuildSrcBuild

        cleanup:
        runReleaseHandle.abort()
        runBlockingHandle.abort()
    }

    Map<String, Long> startedTaskTimes(String output) {
        taskTimes(output, 'started')
    }

    Map<String, Long> finishedTaskTimes(String output) {
        taskTimes(output, 'finished')
    }

    Map<String, Long> taskTimes(String output, String state) {
        output.readLines().collect {
            it =~ /(.*) ${state} at (\d*)/
        }.findAll {
            it.matches()
        }.collectEntries {
            def match = it[0]
            [(match[1]): Long.parseLong(match[2])]
        }
    }

    void writeSharedClassFile(TestFile targetDirectory) {
        def packageDirectory = targetDirectory.createDir("src/main/java/org/gradle/integtest/test")
        new File(packageDirectory, "BuildSrcTask.java").text = """
        package org.gradle.integtest.test;
        import org.gradle.api.DefaultTask;
        import org.gradle.api.tasks.TaskAction;

        public class BuildSrcTask extends DefaultTask{
            @TaskAction public void defaultAction(){
                System.out.println(String.format("BuildSrcTask '%s' executed.", getName()));
            }
        }
        """
    }
}
