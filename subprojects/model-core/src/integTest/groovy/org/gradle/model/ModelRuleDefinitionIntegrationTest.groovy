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

class ModelRuleDefinitionIntegrationTest extends AbstractIntegrationSpec {
    def "rule can define additional rules using a @Rules method"() {
        buildFile << '''
class MyPlugin extends RuleSource {
    @Rules
    void rules(OtherRules rules) {
        println "applying rule source"
        assert rules != null
    }

    @Mutate
    void strings(@Path('strings') List<String> strings) {
        strings << 'b'
    }
}

class OtherRules extends RuleSource {
    @Model
    void strings(List<String> strings) {
        strings << 'a'
    }
}

apply plugin: MyPlugin

model {
    tasks {
        show(Task) {
            doLast {
                println "strings = " + $.strings
            }
        }
    }
}
'''

        when:
        run 'show'

        then:
        output.contains("applying rule source")
        output.contains("strings = [a, b]")
    }

    def "first parameter of @Rules method must be assignable to RuleSource"() {
        buildFile << '''
class MyPlugin extends RuleSource {
    @Rules
    void rules(Project project) {
    }
}
apply plugin: MyPlugin
'''

        expect:
        fails 'model'
        failure.assertHasCause('''Type MyPlugin is not a valid rule source:
- Method MyPlugin#rules is not a valid rule method: first parameter must be a RuleSource subtype''')
    }
}
