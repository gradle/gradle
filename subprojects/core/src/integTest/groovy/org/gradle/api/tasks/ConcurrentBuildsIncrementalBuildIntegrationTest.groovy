/*
 * Copyright 2014 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class ConcurrentBuildsIncrementalBuildIntegrationTest extends AbstractIntegrationSpec {
    @Rule CyclicBarrierHttpServer server1 = new CyclicBarrierHttpServer()
    @Rule CyclicBarrierHttpServer server2 = new CyclicBarrierHttpServer()

    private void prepareTransformTask() {
        buildFile << '''
public class TransformerTask extends DefaultTask {
    private File inputFile
    private File outputFile
    private String format = "[%s]"

    @InputFile
    public File getInputFile() {
        return inputFile
    }

    public void setInputFile(File inputFile) {
        this.inputFile = inputFile
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile
    }

    @Input
    public String getFormat() {
        return format
    }

    public void setFormat(String format) {
        this.format = format
    }

    @TaskAction
    public void transform() {
        outputFile.text = String.format(format, inputFile.text)
    }
}
'''
    }

    def "task history is shared between multiple build processes"() {
        prepareTransformTask()

        buildFile << """
task a(type: TransformerTask) {
    inputFile = file('src.txt')
    outputFile = file('src.a.txt')
}
task block1 {
    dependsOn a
    doLast {
        new URL("$server1.uri").text
    }
}
task block2 {
    dependsOn a
    doLast {
        new URL("$server2.uri").text
    }
}
"""
        TestFile inputFile = file('src.txt')
        inputFile.text = 'content'

        given:
        succeeds "a"

        when:
        // Start build 1 then wait until it has run task 'a'. Should see 'a' is up-to-date
        executer.withTasks("block1")
        def build1 = executer.start()
        def server1WaitForResult = server1.waitFor(false, 120)

        // Change content and start build 2 then wait until it has run task 'a'. Should see 'a' is not up-to-date
        inputFile.text = 'new content'
        executer.withTasks("block2")
        def build2 = executer.start()
        def server2WaitForResult = server2.waitFor(false, 120)

        // Finish up build 1
        server1.release()
        def result1 = build1.waitForFinish()

        // Run build 3 before build 2 has completed. This happens after build 2 has run 'a'. Should see 'a' is up-to-date
        def result3 = executer.withTasks("a").run()

        // Finish up build 2
        server2.release()
        def result2 = build2.waitForFinish()

        then:
        result1.assertTaskSkipped(':a')
        result2.assertTaskNotSkipped(':a')
        result3.assertTaskSkipped(':a')

        server1WaitForResult && server2WaitForResult
    }

    def "can interleave execution of tasks across multiple build processes"() {
        prepareTransformTask()

        buildFile << """
task a(type: TransformerTask) {
    inputFile = file('src.a.txt')
    outputFile = file('out.a.txt')
}
task b(type: TransformerTask) {
    dependsOn a
    inputFile = a.outputFile
    outputFile = file('out.b.txt')
}
task block1 {
    doLast {
        new URL("$server1.uri").text
    }
}
block1.mustRunAfter a
b.mustRunAfter block1

task block2 {
    doLast {
        new URL("$server2.uri").text
    }
}
block2.mustRunAfter b
"""
        TestFile inputAFile = file('src.a.txt')
        inputAFile.text = 'content'

        succeeds('help') // Ensure build scripts are compiled

        when:
        // Start build 1 then wait until it has run task 'a'.
        executer.withTasks("a", "block1", "b")
        def build1 = executer.start()
        def server1WaitForResult = server1.waitFor(false, 120)

        // Start build 2 then wait until it has run both 'a' and 'b'.
        executer.withTasks("a", "b", "block2")
        def build2 = executer.start()
        def server2WaitForResult = server2.waitFor(false, 120)

        // Finish up build 1 and 2
        server1.release() // finish build 1 while build 2 is still running
        def result1 = build1.waitForFinish()
        server2.release()
        def result2 = build2.waitForFinish()

        then:
        result1.assertTaskNotSkipped(':a')
        result1.assertTaskSkipped(':b')
        result2.assertTaskSkipped(':a')
        result2.assertTaskNotSkipped(':b')

        server1WaitForResult && server2WaitForResult
    }
}
