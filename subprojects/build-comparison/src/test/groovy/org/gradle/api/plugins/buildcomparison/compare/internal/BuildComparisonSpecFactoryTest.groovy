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

package org.gradle.api.plugins.buildcomparison.compare.internal

import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.internal.ByTypeAndNameBuildOutcomeAssociator
import spock.lang.Specification

class BuildComparisonSpecFactoryTest extends Specification {

    def "build spec"() {
        given:
        def factory = new BuildComparisonSpecFactory(new ByTypeAndNameBuildOutcomeAssociator(StringBuildOutcome))

        when:
        def result = factory.createSpec(strs("a", "b", "c"), strs("b", "c", "d"))

        then:
        result.from == strs("a", "b", "c")
        result.to == strs("b", "c", "d")
        result.outcomeAssociations.size() == 2
        def associations = result.outcomeAssociations.toList().sort { it.source.name }

        associations[0].source == str("b")
        associations[0].target == str("b")
        associations[0].type == StringBuildOutcome

        associations[1].source == str("c")
        associations[1].target == str("c")
        associations[1].type == StringBuildOutcome
    }

    Set<BuildOutcome> strs(String... strings) {
        strings.collect { str(it) }
    }

    BuildOutcome str(String str) {
        new StringBuildOutcome(str, str)
    }

}
