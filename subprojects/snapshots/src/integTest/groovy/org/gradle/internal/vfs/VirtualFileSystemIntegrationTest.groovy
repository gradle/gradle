/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY
import static org.gradle.internal.service.scopes.VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY

class VirtualFileSystemIntegrationTest extends AbstractIntegrationSpec {

    def "long-lived VFS keeps file system information between builds"() {
        executer.beforeExecute {
            executer.withArguments(
                "-D${VFS_RETENTION_ENABLED_PROPERTY}"
            )
        }

        buildFile << """
            apply plugin: "application"
            
            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        run "run"
        then:
        output.contains "Hello World!"
        executedAndNotSkipped(":compileJava", ":classes", ":run")

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        run "run"
        then:
        output.contains "Hello World!"
        skipped(":compileJava", ":processResources", ":classes")
        executedAndNotSkipped(":run")

        when:
        run "run", "-D${VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY}=${mainSourceFile.absolutePath}"
        then:
        output.contains "Hello VFS!"
        executedAndNotSkipped(":compileJava", ":classes", ":run")
    }

    private static String sourceFileWithGreeting(String greeting) {
        """
            public class Main {
                public static void main(String... args) {
                    System.out.println("$greeting");
                }
            }
        """
    }
}
