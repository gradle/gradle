/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.model.internal.fixture.ProjectRegistrySpec

class ClassModelRuleSourceValidationTest extends ProjectRegistrySpec {
    def extractor = new ModelRuleExtractor([], proxyFactory, schemaStore, structBindingsStore)

    def "invalid #type - #reason"() {
        when:
        extractor.extract(type)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        def message = e.message
        message.contains(reason)

        where:
        type                               | reason
        OuterClass.AnInterface             | "Must be a class, not an interface"
        OuterClass.InnerInstanceClass      | "Enclosed classes must be static and non private"
        new RuleSource() {}.getClass()     | "Enclosed classes must be static and non private"
        OuterClass.HasTwoConstructors      | "Cannot declare a constructor that takes arguments"
        OuterClass.HasInstanceVar          | "Field foo is not valid: Fields must be static final."
        OuterClass.HasFinalInstanceVar     | "Field foo is not valid: Fields must be static final."
        OuterClass.HasNonFinalStaticVar    | "Field foo is not valid: Fields must be static final."
        OuterClass.DoesNotExtendRuleSource | "Rule source classes must directly extend org.gradle.model.RuleSource"
        OuterClass.HasSuperclass           | "Rule source classes must directly extend org.gradle.model.RuleSource"
    }

    def "valid #type"() {
        when:
        extractor.extract(type)

        then:
        noExceptionThrown()

        where:
        type << [
                OuterClass.InnerPublicStaticClass,
                OuterClass.HasExplicitDefaultConstructor,
                OuterClass.HasStaticFinalField
        ]
    }
}
