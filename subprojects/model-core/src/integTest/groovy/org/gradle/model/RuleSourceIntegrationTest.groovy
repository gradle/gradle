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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class RuleSourceIntegrationTest extends AbstractIntegrationSpec {
    def "cannot create a model element of type RuleSource"() {
        buildFile << '''
class Rules extends RuleSource {
}

model {
    rules(Rules) { }
}
'''

        expect:
        fails()
        failure.assertHasCause("Declaration of model rule rules(Rules) { ... } @ build.gradle line 6, column 5 is invalid.")
        failure.assertHasCause("A model element of type: 'Rules' can not be constructed.")
    }
}
