/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import spock.lang.Issue

class ComponentMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest implements ComponentMetadataRulesSupport {
    String getDefaultStatus() {
        GradleMetadataResolveRunner.useIvy()?'integration':'release'
    }

    def setup() {
        buildFile <<
"""
dependencies {
    conf 'org.test:projectA:1.0'
}

// implement Sync manually to make sure that task is never up-to-date
task resolve {
    doLast {
        delete 'libs'
        copy {
            from configurations.conf
            into 'libs'
        }
    }
}
"""
    }

    def "rule receives correct metadata"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

class AssertingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert context.details.id.group == "org.test"
            assert context.details.id.name == "projectA"
            assert context.details.id.version == "1.0"
            assert context.details.status == "$defaultStatus"
            assert context.details.statusScheme == ["integration", "milestone", "release"]
            assert !context.details.changing
    }
}

dependencies {
    components {
        all(AssertingRule)
    }
}
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
    }

    def "changes made by a rule are visible to subsequent rules"() {
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
                """
class UpdatingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            context.details.status "integration.changed" // verify that 'details' is enhanced
            context.details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
            context.details.changing = true
    }
}

class VerifyingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert context.details.status == "integration.changed"
            assert context.details.statusScheme == ["integration.changed", "milestone.changed", "release.changed"]
            assert context.details.changing
    }
}

dependencies {
    components {
        all(UpdatingRule)
        all(VerifyingRule)
    }
}
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
    }

    def "changes made by a rule are not cached"() {
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
                """
class UpdatingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            assert !context.details.changing
            assert context.details.status == "$defaultStatus"
            assert context.details.statusScheme == ["integration", "milestone", "release"]

            context.details.changing = true
            context.details.status = "release.changed"
            context.details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
    }
}

dependencies {
    components {
        all(UpdatingRule)
    }
}
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
        succeeds 'resolve'
    }

    def "can apply all rule types to all modules" () {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []

            class VerifyingRule implements ComponentMetadataRule {
                static boolean ruleInvoked
                
                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            dependencies {
                components {
                    all { ComponentMetadataDetails details ->
                        rulesInvoked << details.id.version
                    }
                    all {
                        rulesInvoked << id.version
                    }
                    all { details ->
                        rulesInvoked << details.id.version
                    }
                    all(new ActionRule('rulesInvoked': rulesInvoked))
                    all(new RuleObject('rulesInvoked': rulesInvoked))
                    all(VerifyingRule)
                }
            }

            class ActionRule implements Action<ComponentMetadataDetails> {
                List rulesInvoked

                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << details.id.version
                }
            }

            class RuleObject {
                List rulesInvoked

                @org.gradle.model.Mutate
                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << details.id.version
                }
            }

            resolve.doLast { 
                assert rulesInvoked == [ '1.0', '1.0', '1.0', '1.0', '1.0' ]
                assert VerifyingRule.ruleInvoked 
            }
        """

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
    }

    def "can apply all rule types by module" () {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """
            ext.rulesInvoked = []
            ext.rulesUninvoked = []

            class InvokedRule implements ComponentMetadataRule {
                static boolean ruleInvoked
                
                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            class NotInvokedRule implements ComponentMetadataRule {
                static boolean ruleInvoked
                
                public void execute(ComponentMetadataContext context) {
                    ruleInvoked = true
                }
            }

            dependencies {
                components {
                    withModule('org.test:projectA') { ComponentMetadataDetails details ->
                        assert details.id.group == 'org.test'
                        assert details.id.name == 'projectA'
                        rulesInvoked << 1
                    }
                    withModule('org.test:projectA', new ActionRule('rulesInvoked': rulesInvoked))
                    withModule('org.test:projectA', new RuleObject('rulesInvoked': rulesInvoked))

                    withModule('org.test:projectB') { ComponentMetadataDetails details ->
                        rulesUninvoked << 1
                    }
                    withModule('org.test:projectB', new ActionRule('rulesInvoked': rulesUninvoked))
                    withModule('org.test:projectB', new RuleObject('rulesInvoked': rulesUninvoked))

                    withModule('org.test:projectA', InvokedRule)
                    withModule('org.test:projectB', NotInvokedRule)
                }
            }

            class ActionRule implements Action<ComponentMetadataDetails> {
                List rulesInvoked

                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << 2
                }
            }

            class RuleObject {
                List rulesInvoked

                @org.gradle.model.Mutate
                void execute(ComponentMetadataDetails details) {
                    rulesInvoked << 3
                }
            }

            resolve.doLast {
                assert rulesInvoked.sort() == [ 1, 2, 3 ]
                assert rulesUninvoked.empty
                assert InvokedRule.ruleInvoked
                assert !NotInvokedRule.ruleInvoked
            }
        """

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
        }

        then:
        succeeds 'resolve'
    }

    def "produces sensible error when @Mutate method does not have ComponentMetadata as first parameter"() {
        buildFile << """
            dependencies {
                components {
                    all(new BadRuleSource())
                }
            }

            class BadRuleSource {
                @org.gradle.model.Mutate
                void doSomething(String s) { }
            }
        """

        when:
        fails "resolve"

        then:
        fails 'resolveConf'
        failureDescriptionStartsWith("A problem occurred evaluating root project")
        failure.assertHasCause("""Type BadRuleSource is not a valid rule source:
- Method doSomething(java.lang.String) is not a valid rule method: First parameter of a rule method must be of type org.gradle.api.artifacts.ComponentMetadataDetails""")
    }

    @RequiredFeatures(
        @RequiredFeature(feature=GradleMetadataResolveRunner.REPOSITORY_TYPE, value="maven")
    )
    def "rule that accepts IvyModuleDescriptor isn't invoked for Maven component"() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile <<
            """
def plainRuleInvoked = false
def ivyRuleInvoked = false

dependencies {
    components {
        all { ComponentMetadataDetails details ->
            plainRuleInvoked = true
        }
        all { ComponentMetadataDetails details, IvyModuleDescriptor descriptor ->
            ivyRuleInvoked = true
        }
    }
}

resolve.doLast {
    assert plainRuleInvoked
    assert !ivyRuleInvoked
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve'
        // also works when already cached
        succeeds 'resolve'
    }

    def 'class based rule does not get access to IvyModuleDescriptor for Maven component'() {
        given:
        repository {
            'org.test:projectA:1.0'()
        }

        buildFile << """
class IvyRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        assert context.getDescriptor(IvyModuleDescriptor) ${GradleMetadataResolveRunner.useIvy()? '!=' : '=='} null
    }
}

dependencies {
    components {
        all(IvyRule)
    }
}
"""
        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'resolve'
    }

    @Issue("gradle/gradle#4261")
    def "different projects can apply different metadata rules for the same component"() {
        repository {
            'org.test:projectA:1.0'()
            'org.test:projectB:1.0'()
        }

        settingsFile << """
rootProject.name = 'root'
include 'sub'
"""
        buildFile << """
class AddDependencyRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.allVariants {
            withDependencies {
                add('org.test:projectB:1.0')
            }
        }
    }
}

project (':sub') {
    $repositoryDeclaration

    configurations {
        conf
        other
    }
    dependencies {
        conf 'org.test:projectA:1.0'
        other 'org.test:projectA:1.0'

        // Component metadata rule that applies only to the 'sub' project
        components {
            withModule('org.test:projectA', AddDependencyRule)
        }
    }
    task res {
        doLast {
            // If we resolve twice the modified component metadata for 'projectA' must not be cached in-memory 
            println configurations.conf.collect { it.name }
            println configurations.other.collect { it.name }
        }
    }
}

task res {
    doLast {
        // Should get the unmodified component metadata for 'projectA'
        println configurations.conf.collect { it.name }
        assert configurations.conf.collect { it.name } == ['projectA-1.0.jar']
    }
}
"""

        when:
        repositoryInteractions {
            'org.test:projectA:1.0' {
                allowAll()
            }
            'org.test:projectB:1.0' {
                allowAll()
            }
        }

        then:
        succeeds ':sub:res', ':res'
    }
}
