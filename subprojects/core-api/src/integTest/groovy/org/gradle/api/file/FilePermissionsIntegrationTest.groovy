/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.TextUtil

class FilePermissionsIntegrationTest extends AbstractIntegrationSpec {

    def "can be used as task input"() {
        given:
        def outputFile = testDirectory.file("output.txt")

        buildKotlinFile << """
            abstract class Producer : DefaultTask() {
                @get:Input
                abstract val permissions: Property<String>

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @TaskAction
                fun produce() {
                    val output = outputFile.get().asFile
                    output.writeText(permissions.get())
                }
            }

            abstract class Consumer : DefaultTask() {
                @get:Input
                abstract val message: Property<Int>

                @get:OutputFile
                abstract val outputFile: RegularFileProperty

                @get:Inject
                abstract val fsOps: FileSystemOperations

                @TaskAction
                fun consume() {
                    outputFile.asFile.get().writeText("Permission: \${Integer.toOctalString(message.get())}")
                }
            }

            val producer = tasks.register<Producer>("producer")
            val consumer = tasks.register<Consumer>("consumer")

            consumer {
                message.set($wiring)
                outputFile.set(File("${TextUtil.normaliseFileSeparators(outputFile.absolutePath)}"))
            }

            producer {
                permissions.set(System.getProperty("permissions", "0777"))
                outputFile.set(layout.buildDirectory.file("file.txt"))
            }
        """.stripIndent()

        when: "1st run"
        succeeds "consumer"

        then: "both producer and consumer get executed"
        executedAndNotSkipped(":producer", ":consumer")
        outputFile.text == "Permission: 777"

        when: "2nd run"
        succeeds "consumer"

        then: "both are skipped, because they are up-to-date"
        skipped(":producer", ":consumer")
        outputFile.text == "Permission: 777"

        when: "3rd run"
        succeeds "consumer", "-Dpermissions=0755"

        then: "both producer and consumer get executed"
        executedAndNotSkipped(":producer", ":consumer")
        outputFile.text == "Permission: 755"

        where:
        wiring << [
            //"fsOps.permissions(producer.flatMap { it.outputFile.asFile }.map { it.readText() }).map { it.toUnixNumeric() }", //TODO: https://github.com/gradle/gradle/issues/19252
            "fsOps.permissions(producer.flatMap { it.outputFile }.map { it.asFile.readText() }).map { it.toUnixNumeric() }"
        ]
    }

}
