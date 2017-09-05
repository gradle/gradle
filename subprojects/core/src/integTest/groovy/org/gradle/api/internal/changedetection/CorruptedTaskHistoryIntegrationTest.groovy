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

class CorruptedTaskHistoryIntegrationTest extends AbstractIntegrationSpec {

    def "broken build doesn't corrupt the artifact history"() {
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
        (1..100).each {
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
    
    @InputDirectory
    File inputDir
    
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
                Thread.sleep(400)
                System.exit(0)
            }).start()
        }
    }
}   

apply plugin: 'base'

task createFiles

(1..100).each { num ->
    createFiles.dependsOn(tasks.create("createFiles\${num}", CreateFilesTask) {
        inputDir = file("inputs")
        outputDir = file("build/output\${num}")
    })           
}

        """
        file('inputs').create {
            (1..100).each {
                file("input${it}.txt").text = "input${it}"
            }
        }

        when:
        succeeds("createFiles")
        succeeds("clean")
        fails("createFiles", "-PkillMe=true", "--max-workers=40")

        then:
        file('build/output10').allDescendants().size() == 100

        expect:
        succeeds "createFiles"
    }
}
