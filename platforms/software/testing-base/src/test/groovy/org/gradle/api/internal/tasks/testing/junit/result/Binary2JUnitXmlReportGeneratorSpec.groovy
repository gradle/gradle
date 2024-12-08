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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.internal.tasks.testing.BuildableTestResultsProvider
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationExecutorSupport
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class Binary2JUnitXmlReportGeneratorSpec extends Specification {

    @Rule
    private TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())
    private TestResultsProvider resultsProvider
    BuildOperationRunner buildOperationRunner = new TestBuildOperationRunner()
    BuildOperationExecutor buildOperationExecutor
    Binary2JUnitXmlReportGenerator generator

    def generatorWithMaxThreads(int numThreads) {
        buildOperationExecutor = BuildOperationExecutorSupport.builder(numThreads)
            .withRunner(buildOperationRunner)
            .build()
        Binary2JUnitXmlReportGenerator reportGenerator = new Binary2JUnitXmlReportGenerator(
            temp.testDirectory,
            resultsProvider,
            new JUnitXmlResultOptions(false, false, true, true),
            buildOperationRunner,
            buildOperationExecutor,
            "localhost")
        return reportGenerator
    }

    def "writes results - #numThreads parallel thread(s)"() {
        resultsProvider = new BuildableTestResultsProvider().tap {
            result("root-1")
            child {
                resultForClass("FooTest")
                child {
                    result("test-a")
                }
            }
            child {
                resultForClass("BarTest")
                child {
                    result("test-a")
                }
            }
        }
        generator = generatorWithMaxThreads(numThreads)

        when:
        generator.generate()

        then:
        temp.testDirectory.file("TEST-FooTest.xml").assertExists()
        temp.testDirectory.file("TEST-BarTest.xml").assertExists()

        where:
        numThreads << [1, 4]
    }

    def "adds context information to the failure if something goes wrong"() {
        resultsProvider = new BuildableTestResultsProvider().tap {
            result("root-1")
            child {
                resultForClass("FooTest")
                child {
                    result("test-a")
                }
            }
        }
        generator = generatorWithMaxThreads(1)

        generator.xmlWriter = Mock(JUnitXmlResultWriter)
        generator.xmlWriter.write(_, _) >> { throw new IOException("Boo!") }

        when:
        generator.generate()

        then:
        def ex = thrown(MultipleBuildOperationFailures)
        ex.causes.size() == 1
        ex.causes[0].message.startsWith('Could not write XML test results for FooTest')
        ex.causes[0].cause.message == "Boo!"
    }
}
