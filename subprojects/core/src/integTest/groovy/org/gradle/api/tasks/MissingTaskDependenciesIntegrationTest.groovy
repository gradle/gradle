/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

@Unroll
class MissingTaskDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BlockingHttpServer server = new BlockingHttpServer()

    private static final String DEPRECATION_WARNING = ":consumer consumes the output of :producer, but does not declare a dependency. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. Execution optimizations are disabled due to the failed validation. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."

    def "detects missing dependency between two tasks (#description)"() {
        buildFile << """
            task producer {
                def outputFile = file("${producedLocation}")
                outputs.${producerOutput}
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("${consumedLocation}")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION_WARNING)
        then:
        succeeds("producer", "consumer")

        when:
        executer.expectDocumentedDeprecationWarning(DEPRECATION_WARNING)
        then:
        succeeds("consumer", "producer")

        where:
        description            | producerOutput     | producedLocation           | consumedLocation
        "same location"        | "file(outputFile)" | "output.txt"               | "output.txt"
        "consuming ancestor"   | "file(outputFile)" | "build/dir/sub/output.txt" | "build/dir"
        "consuming descendant" | "dir('build/dir')" | "build/dir/sub/output.txt" | "build/dir/sub/output.txt"
    }

    def "does not detect missing dependency when consuming the sibling of the output of the producer"() {
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("build/notOutput.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        expect:
        succeeds("producer", "consumer")
        succeeds("consumer", "producer")
    }

    def "transitive dependencies are accepted as valid dependencies (including #dependency)"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            task a
            task b
            task c
            task d

            consumer.dependsOn(d)

            d.dependsOn(c)
            ${dependency}
            b.dependsOn(a)

            a.dependsOn(producer)
        """

        expect:
        // We add the intermediate tasks here, since the dependency relation doesn't necessarily force their scheduling
        succeeds("producer", "b", "c", "consumer")

        where:
        dependency            | _
        "c.dependsOn(b)"      | _
        "c.mustRunAfter(b)"   | _
        "b.finalizedBy(c)"    | _
    }

    def "only having shouldRunAfter causes a validation warning"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            consumer.shouldRunAfter(producer)
        """

        expect:
        executer.expectDocumentedDeprecationWarning(DEPRECATION_WARNING)
        succeeds("producer", "consumer")
    }

    def "detects missing dependencies even if the consumer does not have outputs"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                inputs.files(inputFile)
                doLast {
                    println "Hello " + inputFile.text
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning(DEPRECATION_WARNING)
        succeeds("producer", "consumer")
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    // no point, always runs in parallel
    def "fails when consumer and producer run in parallel"() {
        server.start()
        settingsFile << """
            include ':a', ':b'
        """
        file('a/build.gradle') << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                outputs.upToDateWhen { false }
                doLast {
                    ${server.callFromTaskAction("taskAction")}
                    outputFile.text = "produced"
                }
            }
        """
        file("b/build.gradle") << """
            task consumer {
                def inputFile = file("../a/output.txt")
                inputs.files(inputFile)
                doLast {
                    ${server.callFromTaskAction("taskAction")}
                    println "Hello " + inputFile.text
                }
            }
        """
        executer.beforeExecute {
            withArgument("--max-workers=2")
        }

        when:
        // Make sure the input file exists
        server.expect("taskAction")
        then:
        succeeds("producer")

        when:
        server.expect("taskAction")
        def result = fails(":b:consumer", ":a:producer")
        then:
        result.assertHasCause(":b:consumer consumes the output of :a:producer, but does not declare a dependency.")
    }

}
