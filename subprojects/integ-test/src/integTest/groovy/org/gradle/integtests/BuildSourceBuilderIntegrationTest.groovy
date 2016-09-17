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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class BuildSourceBuilderIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://issues.gradle.org/browse/GRADLE-2032")
    def "can simultaneously run gradle on projects with buildSrc"() {
        given:
        def buildSrcDir = file("buildSrc").createDir()
        writeSharedClassFile(buildSrcDir);
        buildFile.text = """
        import org.gradle.integtest.test.BuildSrcTask

        task blocking(type:BuildSrcTask) {
            doLast {
                file("run1washere.lock").createNewFile()
                while(!file("run2washere.lock").exists()){
                    sleep 10
                }
            }
        }

        task releasing(type:BuildSrcTask) {
            doLast {
                while(!file("run1washere.lock").exists()){
                    sleep 10
                }
                file("run2washere.lock").createNewFile()
            }
        }
        """
        when:
        def handleRun1 = executer.withTasks("blocking").start()
        def handleRun2 = executer.withTasks("releasing").start()
        and:
        def finish2 = handleRun2.waitForFinish()
        def finish1 = handleRun1.waitForFinish()
        then:
        finish1.error.equals("")
        finish2.error.equals("")
        finish1.assertTasksExecuted(":blocking")
        finish2.assertTasksExecuted(":releasing")
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