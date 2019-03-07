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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.workers.WorkerExecutor
import org.junit.Rule
import spock.lang.Issue

class CorruptedTaskHistoryIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    // If this test starts to be flaky it points to a problem in the code!
    // See the linked issue.
    @Issue("https://github.com/gradle/gradle/issues/2827")
    def "broken build doesn't corrupt the artifact history"() {
        server.start()
        def numberOfFiles = 10
        def numberOfOutputFilesPerTask = numberOfFiles
        def numberOfInputProperties = 10
        def numberOfTasks = 100
        def totalNumberOfOutputDirectories = numberOfTasks
        def numberOfExecutedTaskBeforeKilling = 11
        def totalNumberOfOutputFiles = numberOfTasks * numberOfOutputFilesPerTask + totalNumberOfOutputDirectories

        setupTestProject(numberOfFiles, numberOfInputProperties, numberOfTasks)

        executer.beforeExecute {
            // We need a separate JVM in order not to kill the test JVM
            requireGradleDistribution()
            requireIsolatedDaemons()
            requireDaemon()
        }

        when:
        succeeds("createFiles")
        succeeds("clean")

        // Worker gate
        def workerHandler = server.expectConcurrentAndBlock(numberOfTasks, (1..numberOfTasks).collect { "worker-for-output$it" } as String[])

        // Kill gate
        def killHandler = server.expectOptionalAndBlock(numberOfExecutedTaskBeforeKilling, (1..numberOfTasks).collect { "kill-ready-for-output$it" } as String[])

        // Start Gradle in the background
        def handle = executer.withArguments("createFiles", "-DkillMe=true", "--max-workers=${numberOfTasks}").start()

        // Wait for all worker to start
        workerHandler.waitForAllPendingCalls()

        // Release only a few of them
        workerHandler.release(numberOfExecutedTaskBeforeKilling)

        // Wait until workers finish their jobs
        killHandler.waitForAllPendingCalls()

        // Kill the daemon
        killRunningDaemon()

        // Wait for Gradle to fail
        handle.waitForFailure()

        def createdFiles = file('build').allDescendants().size() as BigDecimal
        println "\nNumber of created files when the process has been killed: ${createdFiles}"

        then:
        createdFiles in ((0.1 * totalNumberOfOutputFiles)..(0.9 * totalNumberOfOutputFiles))

        expect:
        succeeds "createFiles"
    }

    void killRunningDaemon() {
        def fixture = DaemonLogsAnalyzer.newAnalyzer(executer.daemonBaseDir, executer.distribution.version.version)
        assert fixture.daemons.size() == 1
        def daemon = fixture.daemons.first()
        daemon.assertBusy()
        daemon.kill()
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
     * If the Gradle property {@code 'killMe'} is set to some truthy value, we start a {@link Thread} which checks every {@code killPollInterval} ms if there are more than 40 output directories in 'build` and kills the Gradle process if there are.
     * Finally, there is one task depending on all the tasks which are creating files. This one is just called {@code createFiles}.
     */
    private void setupTestProject(int numberOfFiles, int numberOfInputProperties, int numberOfTasks) {
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
        if (Boolean.valueOf(System.getProperty("killMe"))) {
            ${server.callFromBuildUsingExpression('"worker-for-${outputDir.name}"')}
        }
        (1..$numberOfFiles).each {
            new File(outputDir, "\${it}.txt").text = "output\${it}"
        }
        if (Boolean.valueOf(System.getProperty("killMe"))) {
            ${server.callFromBuildUsingExpression('"kill-ready-for-${outputDir.name}"')}
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

    private static String inputProperty(Integer postfix) {
        """
            @InputDirectory
            File inputDir${postfix}       
        """
    }
}
