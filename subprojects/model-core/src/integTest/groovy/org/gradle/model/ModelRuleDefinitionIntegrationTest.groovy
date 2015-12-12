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
import org.gradle.language.base.LanguageSourceSet

class ModelRuleDefinitionIntegrationTest extends AbstractIntegrationSpec {
    def "@Rule method can apply rules to a particular target"() {
        buildFile << '''
@Managed
interface Thing {
    String getName()
    void setName(String name)
}

class MyPlugin extends RuleSource {
    @Model
    void p1(Thing t) {
        assert t.name == 'default'
        t.name = 'p1'
    }

    @Model
    void p2(Thing t) {
        assert t.name == null
        t.name = 'p2'
    }

    @Rules
    void rules(CalculateName rules, @Path('p1') Thing t) {
        println "applying rules to $t"
        assert rules != null
        assert t != null
    }
}

class CalculateName extends RuleSource {
    @Defaults
    void defaultName(Thing t) {
        t.name = 'default'
    }

    @Finalize
    void finalizeName(Thing t) {
        assert t.name == 'p1'
        t.name = 'p1 finalized'
    }
}

apply plugin: MyPlugin

model {
    tasks {
        show(Task) {
            doLast {
                println "p1 = " + $.p1.name
                println "p2 = " + $.p2.name
            }
        }
    }
}
'''

        when:
        run 'show'

        then:
        output.contains("applying rules to Thing 'p1'")
        output.contains("p1 = p1 finalized")
        output.contains("p2 = p2")
    }

    def "reports failure in @Rules method"() {
        buildFile << '''
class MyPlugin extends RuleSource {
    @Model
    void strings(List<String> s) {}

    @Rules
    void rules(RuleSource rules, List<String> s) {
        throw new RuntimeException("broken")
    }
}

apply plugin: MyPlugin
'''
        expect:
        fails("model")
        failure.assertHasCause("Exception thrown while executing model rule: MyPlugin#rules")
        failure.assertHasCause("broken")
    }

    def "first parameter of @Rules method must be assignable to RuleSource"() {
        buildFile << '''
class MyPlugin extends RuleSource {
    @Rules
    void rules(LanguageSourceSet lss, String string) {
    }
}
apply plugin: MyPlugin
'''

        expect:
        fails 'model'
        failure.assertHasCause("""Type MyPlugin is not a valid rule source:
- Method rules(${LanguageSourceSet.name}, ${String.name}) is not a valid rule method: The first parameter of a method annotated with @Rules must be a subtype of ${RuleSource.name}""")
    }
}
