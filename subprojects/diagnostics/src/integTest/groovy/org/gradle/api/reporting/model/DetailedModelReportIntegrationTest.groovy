/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseFileSeparators

/**
 * Tests for more detailed output i.e. with the `org.gradle.model.dsl` flag enabled.
 */
class DetailedModelReportIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final HttpServer server = new HttpServer()

    void setup() {
        EnableModelDsl.enable(executer)
    }

    def "includes a relative path to the build script"() {
        given:
        buildFile << """
${managedNumbers()}

model {
    numbers(Numbers){
        value = 5
    }
}
"""
        buildFile
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ build.gradle line 9, column 5"
    }

    def "can find the relative path to a custom named build script"() {
        given:
        def renamed = testDirectory.file("why.gradle")
        settingsFile << """
            rootProject.buildFileName = "why.gradle"
        """
        renamed << """
${managedNumbers()}

model {
    numbers(Numbers){
        value = 5
    }
}

"""
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ why.gradle line 9, column 5"
    }

    def "can find the relative path when model configuration is applied from a local script inside the root dir"() {
        given:
        def modelFile = testDirectory.file(inputFile)
        modelFile << """
        ${managedNumbers()}

        model {
            numbers(Numbers){
                value = 5
            }
        }
"""

        buildFile << "apply from: '${inputFile}'"
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ ${inputFile} line 9, column 13"

        where:
        inputFile << ["my-model.gradle", "level1/level2/my-model.gradle"]
    }

    def "can find the relative path when model configuration is applied from a local script outside the root dir"() {
        given:
        def modelFile = testDirectory.getParentFile().file("my-model.gradle")

        modelFile.text = """
        ${managedNumbers()}

        model {
            numbers(Numbers){
                value = 5
            }
        }
"""

        buildFile << "apply from: '${normaliseFileSeparators(modelFile.getAbsolutePath())}'"
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ ../my-model.gradle line 9, column 13"
    }

    def "can find the relative path when model configuration is applied from a remote http url"() {
        given:
        server.start()
        server.logRequests = false

        def modelFile = testDirectory.file("local-my-model.gradle")
        modelFile.text = """
        ${managedNumbers()}

        model {
            numbers(Numbers){
                value = 5
            }
        }
"""

        server.allowGetOrHead("/stub/my-model.gradle", modelFile)
        buildFile << "apply from: '${server.getAddress()}/stub/my-model.gradle'"
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ ${server.getAddress()}/stub/my-model.gradle line 9, column 13"
    }

    def "can find the relative path to model rules defined in different scripts"() {
        given:
        def model1File = testDirectory.file("model1.gradle")
        def model2File = testDirectory.file("sub-dir", "model2.gradle")

        model1File << """
        ${managedNumbers()}
        model {
            numbers(Numbers){
                value = 5
            }
        }
"""

        model2File << """
        ${managedNumbers()}
        model {
            otherNumbers(Numbers){
                value = 5
            }
        }
"""

        buildFile << """
apply from: '${normaliseFileSeparators(model1File.absolutePath)}'
apply from: '${normaliseFileSeparators(model2File.absolutePath)}'
"""
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ model1.gradle line 8, column 13"
        normaliseFileSeparators(modelNode.otherNumbers.@creator[0]) == "model.otherNumbers @ sub-dir/model2.gradle line 8, column 13"
    }

    private String managedNumbers() {
        return """@Managed
        public interface Numbers {
            Integer getValue()
            void setValue(Integer i)
        }"""
    }
}


