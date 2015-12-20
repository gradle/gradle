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

package org.gradle.model.internal.inspect

import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.RuleSource
import org.gradle.model.Rules
import org.gradle.model.internal.fixture.ProjectRegistrySpec

class RuleDefinitionRuleExtractorTest extends ProjectRegistrySpec {
    def extractor = new ModelRuleExtractor([new RuleDefinitionRuleExtractor()], proxyFactory, schemaStore)

    static class InvalidSignature extends RuleSource {
        @Rules
        void broken1(String string, RuleSource ruleSource) {
        }

        @Rules
        void broken2() {
        }

        @Rules
        String broken3(String string) {
            "broken"
        }
    }

    def "rule method must have first parameter that is assignable to RuleSource and have void return type"() {
        when:
        extractor.extract(InvalidSignature)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${InvalidSignature.name} is not a valid rule source:
- Method broken3(java.lang.String) is not a valid rule method: A method annotated with @Rules must have void return type.
- Method broken3(java.lang.String) is not a valid rule method: A method annotated with @Rules must have at least two parameters
- Method broken1(java.lang.String, ${RuleSource.name}) is not a valid rule method: The first parameter of a method annotated with @Rules must be a subtype of ${RuleSource.name}
- Method broken2() is not a valid rule method: A method annotated with @Rules must have at least two parameters"""
    }
}
