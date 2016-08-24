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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

class JavaExecIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src", "main", "java").mkdirs()

        file("src", "main", "java", "Driver.java").write """
            package driver;

            import java.io.*;

            public class Driver {
                public static void main(String[] args) {
                    try {
                        FileWriter out = new FileWriter("out.txt");
                        out.write(args[0]);
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """


        buildFile.write """
            apply plugin: "java"

            task run(type: JavaExec) {
                classpath = project.files(compileJava)
                main "driver.Driver"
                args "1"
            }
        """
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "java exec is not incremental by default"() {
        when:
        run "run"

        then:
        ":run" in nonSkippedTasks

        when:
        run "run"

        then:
        ":run" in nonSkippedTasks
    }

    @Issue(["GRADLE-1483", "GRADLE-3528"])
    def "when the user declares outputs it becomes incremental"() {
        given:
        buildFile << """
            run.outputs.file "out.txt"
        """

        when:
        run "run"

        then:
        ":run" in nonSkippedTasks

        when:
        run "run"

        then:
        ":run" in skippedTasks

        when:
        file("out.txt").delete()

        and:
        run "run"

        then:
        ":run" in nonSkippedTasks
    }
}
