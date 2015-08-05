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

import static org.gradle.util.TextUtil.normaliseFileSeparators

/**
 * Tests for more detailed output i.e. with the `org.gradle.model.dsl` flag enabled.
 */
class DetailedModelReportIntegrationTest extends AbstractIntegrationSpec {

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

    def "can find the relative path when model configuration is via an apply from"() {
        given:
        def modelFile = testDirectory.file("my-model.gradle")
        modelFile << """
        ${managedNumbers()}

        model {
            numbers(Numbers){
                value = 5
            }
        }
"""

        buildFile << "apply from: '${modelFile.name}'"
        when:
        run "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        normaliseFileSeparators(modelNode.numbers.@creator[0]) == "model.numbers @ my-model.gradle line 9, column 13"
    }

    private String managedNumbers() {
        return """@Managed
        public interface Numbers {
            Integer getValue()
            void setValue(Integer i)
        }"""
    }
}


