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
            new File(outputDir, "\${it}.txt").text = "\${it}"
        }
    }
}            

class CreateFilesTask extends DefaultTask {
    private final WorkerExecutor workerExecutor
    
    @Inject
    CreateFilesTask(WorkerExecutor workerExecutor) { 
        this.workerExecutor=workerExecutor
    }
    
    ${
            (1..10).collect {
                '''@InputDirectory
            File inputDir''' + it
            }.join("\n")
        }
    
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
                Thread.sleep(300)
                System.exit(1)
            }).start()
        }
    }
}   

apply plugin: 'base'

task createFiles

(1..100).each { num ->
    createFiles.dependsOn(tasks.create("createFiles\${num}", CreateFilesTask) {
        ${
            (1..10).collect {
                '''inputDir''' + it + ''' = file("inputs")
            '''
            }.join("\n")
        }
            outputDir = file("build/output\${num}")
    })           
}

        """
        file('inputs').create {
            (1..numberOfFiles).each {
                file("input${it}.txt").text = "input${it}"
            }
        }

        executer.beforeExecute {
            // We need a separate JVM in order not to kill the test JVM
            requireGradleDistribution()
        }

        when:
        succeeds("createFiles")
        succeeds("clean")
        fails("createFiles", "-PkillMe=true", "--max-workers=10")

        then:
        file('build/output10').allDescendants().size() == numberOfFiles

        expect:
        succeeds "createFiles"
    }
}
