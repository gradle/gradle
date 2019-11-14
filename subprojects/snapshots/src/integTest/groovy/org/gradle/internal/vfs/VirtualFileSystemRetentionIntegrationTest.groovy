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

class VirtualFileSystemRetentionIntegrationTest extends AbstractIntegrationSpec {

    def "source file changes are recognized after change is injected"() {
        buildFile << """
            apply plugin: "application"
            
            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withRetentionRun "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withRetentionRun "run"
        then:
        outputContains "Hello World!"
        skipped(":compileJava", ":processResources", ":classes")
        executedAndNotSkipped ":run"

        when:
        withRetentionRun "run", "-D${VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY}=${mainSourceFile.absolutePath}"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "buildSrc changes are recognized after change is injected"() {
        def taskSourceFile = file("buildSrc/src/main/java/PrinterTask.java")
        taskSourceFile.text = taskWithGreeting("Hello from original task!")

        buildFile << """
            task hello(type: PrinterTask)
        """

        when:
        withRetentionRun "hello"
        then:
        outputContains "Hello from original task!"

        when:
        taskSourceFile.text = taskWithGreeting("Hello from modified task!")
        withRetentionRun "hello"
        then:
        outputContains "Hello from original task!"

        when:
        withRetentionRun "hello", "-D${VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY}=${taskSourceFile.absolutePath}"
        then:
        outputContains "Hello from modified task!"
    }

    def "build script changes get recognized"() {
        when:
        buildFile.text = """
            println "Hello from the build!"
        """
        withRetentionRun "help"
        then:
        outputContains "Hello from the build!"

        when:
        buildFile.text = """
            println "Hello from the modified build!"
        """
        withRetentionRun "help"
        then:
        outputContains "Hello from the modified build!"
    }

    def "settings script changes get recognized"() {
        when:
        settingsFile.text = """
            println "Hello from settings!"
        """
        withRetentionRun "help"
        then:
        outputContains "Hello from settings!"

        when:
        settingsFile.text = """
            println "Hello from modified settings!"
        """
        withRetentionRun "help"
        then:
        outputContains "Hello from modified settings!"
    }

    def "source file changes are recognized when retention has just been enabled"() {
        buildFile << """
            apply plugin: "application"
            
            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withoutRetentionRun "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withRetentionRun "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    def "source file changes are recognized when retention has just been disabled"() {
        buildFile << """
            apply plugin: "application"
            
            application.mainClassName = "Main"
        """

        def mainSourceFile = file("src/main/java/Main.java")
        mainSourceFile.text = sourceFileWithGreeting("Hello World!")

        when:
        withRetentionRun "run"
        then:
        outputContains "Hello World!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"

        when:
        mainSourceFile.text = sourceFileWithGreeting("Hello VFS!")
        withoutRetentionRun "run"
        then:
        outputContains "Hello VFS!"
        executedAndNotSkipped ":compileJava", ":classes", ":run"
    }

    private void withRetentionRun(String... tasks) {
        executer.withArgument  "-D${VFS_RETENTION_ENABLED_PROPERTY}"
        run tasks
    }

    private void withoutRetentionRun(String... tasks) {
        run tasks
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

    private static String taskWithGreeting(String greeting) {
        """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class PrinterTask extends DefaultTask {
                @TaskAction
                public void execute() {
                    System.out.println("$greeting");
                }
            }
        """
    }
}
