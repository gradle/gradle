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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class JavaExecWithExecutableJarIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("src", "main", "java").mkdirs()

        file("src", "main", "java", "Driver.java").write """
            package driver;

            import java.io.*;

            public class Driver {
                public static void main(String[] args) {
                    try {
                        FileWriter out = new FileWriter("out.txt");
                        out.write("Output");
                        out.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """

        file("src", "main", "resources", "META-INF").mkdirs()

        file("src", "main", "resources", "META-INF", "MANIFEST.MF").write """
            Manifest-Version: 1.0
            Main-Class: driver.Driver
        """


        buildFile.write """
            apply plugin: "java"

            task run(type: JavaExec) {
                executableJar = project.files(compileJava)
            }
        """
    }

    @Issue("GRADLE-1346")
    def "executable jar should be started"() {
        when:
        run "run"

        then:
        file("out.txt").exists()
    }

}
