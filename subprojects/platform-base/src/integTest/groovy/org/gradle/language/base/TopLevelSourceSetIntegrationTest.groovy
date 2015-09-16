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

package org.gradle.language.base

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TopLevelSourceSetIntegrationTest extends AbstractIntegrationSpec {
    def "can create a top level functional source set with a rule"() {
        buildFile << """
        apply plugin: 'language-base'

        class Rules extends RuleSource {
            @Model
            void functionalSources(FunctionalSourceSet sources) {
            }
        }
        apply plugin: Rules
        """

        expect:
        succeeds "components"
    }

    def "can create a top level functional source set via the model dsl"() {
        buildFile << """
        apply plugin: 'language-base'

        model {
            functionalSources(FunctionalSourceSet)
        }
        """

        expect:
        succeeds "components"
    }

    def "model report renders a functional source set"() {
        buildFile << """
        apply plugin: 'language-base'

        model {
            functionalSources(FunctionalSourceSet)
        }
        """

        when:
        succeeds "model"

        then:
        def modelNode = ModelReportOutput.from(output).modelNode
        modelNode.functionalSources.@creator[0] == "model.functionalSources"
        modelNode.functionalSources.@type[0] == "org.gradle.language.base.FunctionalSourceSet"
        modelNode.functionalSources.@nodeValue[0] == "source set 'functionalSources'"
    }
}
