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

package org.gradle.api.internal.changedetection

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class CorruptedTaskHistoryIntegrationTest extends AbstractIntegrationSpec {

    // If this test starts to be flaky it points to a problem in the code!
    // See the linked issue.
    @Issue("https://github.com/gradle/gradle/issues/2827")
    def "broken build doesn't corrupt the artifact history"() {
        def numberOfFiles = 10
        def numberOfOutputFilesPerTask = numberOfFiles
        def numberOfInputProperties = 10
        def numberOfTasks = 100
        def totalNumberOfOutputDirectories = numberOfTasks
        def millisecondsToKill = 300
        def totalNumberOfOutputFiles = numberOfTasks * numberOfOutputFilesPerTask + totalNumberOfOutputDirectories

        setupTestProject(numberOfFiles, numberOfInputProperties, numberOfTasks, millisecondsToKill)

        executer.beforeExecute {
            // We need a separate JVM in order not to kill the test JVM
            requireGradleDistribution()
        }

        when:
        succeeds("createFiles")
        succeeds("clean")
        fails("createFiles", "-PkillMe=true", "--max-workers=${numberOfTasks}")

        then:
        file('build').allDescendants().size() in ((0.1 * totalNumberOfOutputFiles)..(0.9 * totalNumberOfOutputFiles))

        expect:
        succeeds "createFiles"
    }

    /**
     * Setup the test project. <br />
     *
     * The test project is setup in a way that the Gradle build can be killed while it is writing to the task history repository, possibly corrupting it. <br />
     *
     * We create {@code numberOfTasks} tasks, called {@code createFiles${number}}.
     * Each of those tasks has {@code numberOfInputProperties} directory inputs, each one of them pointing to the input directory {@code 'inputs'}.
     * The {@code 'inputs'} directory contains {@code numberOfFiles}.
     * The {@code createFiles${number}} tasks create {@code numberOfFiles} files into the output directory {@code 'build/output${number}'} by using the worker API. So the task actions execute in parallel.
     * If the Gradle property {@code 'killMe'} is set to some truthy value, each of those tasks will a new {@link Thread} which will exit the Gradle JVM {@code millisecondsToKill} ms after the task started executing.
     * Finally, there is one task depending on all the tasks which are creating files. This one is just called {@code createFiles}.
     */
    private void setupTestProject(int numberOfFiles, int numberOfInputProperties, int numberOfTasks, int millisecondsToKill) {
        buildFile << """
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

class CreateFiles implements Runnable {

    private final File outputDir
     
    @Inject
    CreateFiles(File outputDir) {
        this.outputDir=outputDir
    }

    @Override
    void run() {
        (1..$numberOfFiles).each {
            new File(outputDir, "\${it}.txt").text = "output\${it}"
        }
    }
}            

class CreateFilesTask extends DefaultTask {
    private final WorkerExecutor workerExecutor
    
    @Inject
    CreateFilesTask(WorkerExecutor workerExecutor) { 
        this.workerExecutor=workerExecutor
    }
    
    ${(1..numberOfInputProperties).collect { inputProperty(it) }.join("\n")}
    
    @OutputDirectory
    File outputDir

    @TaskAction
    void createFiles() {
        workerExecutor.submit(CreateFiles) {
            isolationMode = IsolationMode.NONE
            params(outputDir)
        }
        if (project.findProperty("killMe")) {
            new Thread({
                while (true) {
                    Thread.sleep(${millisecondsToKill})
                    if (outputDir.parentFile.listFiles().size() > 40) {
                        System.exit(1)
                    }
                }
            }).start()
        }
    }
}   

apply plugin: 'base'

task createFiles

(1..$numberOfTasks).each { num ->
    createFiles.dependsOn(tasks.create("createFiles\${num}", CreateFilesTask) {
            ${(1..numberOfInputProperties).collect { "inputDir$it = file('inputs')" }.join("\n")}
            outputDir = file("build/output\${num}")
    })           
}

        """

        file('inputs').create {
            (1..numberOfFiles).each {
                file("input${it}.txt").text = "input${it}"
            }
        }
    }

    private String inputProperty(Integer postfix) {
        """
            @InputDirectory
            File inputDir${postfix}       
        """
    }
}
