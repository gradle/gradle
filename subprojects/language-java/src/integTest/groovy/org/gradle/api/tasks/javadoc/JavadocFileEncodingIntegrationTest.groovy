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

package org.gradle.api.tasks.javadoc

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavadocFileEncodingIntegrationTest extends AbstractIntegrationSpec {
    @NotYetImplemented
    def "build is not up-to-date when file.encoding changes"() {
        buildFile << """
            apply plugin: 'java'
            javadoc {
                options {
                    windowTitle = "💩 💩 💩 💩"
                }
            }
        """
        executer.requireIsolatedDaemons()
        executer.requireDaemon()

        file("src/main/java/Main.java") << """
            public class Main {}
        """

        when:
        // Doesn't support the characters used in the title
        executer.useOnlyRequestedJvmOpts()
        executer.withBuildJvmOpts("-Dfile.encoding=CP1250")
        succeeds("javadoc")
        then:
        file("build/docs/javadoc/index.html").text.contains("<title>? ? ? ?</title>")
        file("build/tmp/javadoc/javadoc.options").text.contains("-windowtitle '? ? ? ?'")
        result.assertTaskNotSkipped(":javadoc")

        when:
        executer.withBuildJvmOpts("-Dfile.encoding=UTF-8")
        succeeds("javadoc")
        then:
        file("build/docs/javadoc/index.html").text.contains("<title>💩 💩 💩 💩</title>")
        file("build/tmp/javadoc/javadoc.options").text.contains("-windowtitle '💩 💩 💩 💩'")
        result.assertTaskNotSkipped(":javadoc")
    }

    // Simplified version of the above.
    @NotYetImplemented
    def "file.encoding impacts task implementation"() {
        buildFile << """
            class WriteString extends DefaultTask {
                @Input String message
                @OutputFile File outputFile = new File(project.buildDir, "message.txt")
                @TaskAction
                void write() {
                    outputFile.text = message
                }
            }

            task writer(type: WriteString) {
                message = "💩 💩 💩 💩"
            }
        """
        executer.requireIsolatedDaemons()
        executer.requireDaemon()

        when:
        // Message is UTF-8
        executer.useOnlyRequestedJvmOpts()
        executer.withBuildJvmOpts("-Dfile.encoding=CP1250")
        succeeds("writer")
        then:
        file("build/message.txt").text == "? ? ? ?"

        when:
        executer.withBuildJvmOpts("-Dfile.encoding=UTF-8")
        succeeds("writer")
        then:
        file("build/message.txt").text == "💩 💩 💩 💩"
    }
}
