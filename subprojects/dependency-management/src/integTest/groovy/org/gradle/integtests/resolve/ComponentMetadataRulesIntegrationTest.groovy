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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.HttpRepository

abstract class ComponentMetadataRulesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    abstract HttpRepository getRepo()
    abstract String getRepoDeclaration()
    abstract String getDefaultStatus()

    def setup() {
        buildFile <<
"""
$repoDeclaration

configurations { compile }

dependencies {
    compile 'org.test:projectA:1.0'
}

// implement Sync manually to make sure that task is never up-to-date
task resolve {
    doLast {
        delete 'libs'
        copy {
            from configurations.compile
            into 'libs'
        }
    }
}
"""
    }

    def "rule receives correct metadata"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()
        buildFile <<
"""
dependencies {
    components {
        all { ComponentMetadataDetails details ->
            assert details.id.group == "org.test"
            assert details.id.name == "projectA"
            assert details.id.version == "1.0"
            assert details.status == "$defaultStatus"
            assert details.statusScheme == ["integration", "milestone", "release"]
            assert !details.changing
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }

    def "changes made by a rule are visible to subsequent rules"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()

        buildFile <<
                """
dependencies {
    components {
        all { ComponentMetadataDetails details ->
            details.status "integration.changed" // verify that 'details' is enhanced
            details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
            details.changing = true
        }
        all { ComponentMetadataDetails details ->
            assert details.status == "integration.changed"
            assert details.statusScheme == ["integration.changed", "milestone.changed", "release.changed"]
            assert details.changing
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }

    def "changes made by a rule are not cached"() {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()

        buildFile <<
                """
dependencies {
    components {
        all { ComponentMetadataDetails details ->
            assert !details.changing
            assert details.status == "$defaultStatus"
            assert details.statusScheme == ["integration", "milestone", "release"]

            details.changing = true
            details.status = "release.changed"
            details.statusScheme = ["integration.changed", "milestone.changed", "release.changed"]
        }
    }
}
"""

        expect:
        succeeds 'resolve'
        succeeds 'resolve'
    }

    def "can apply all rule types to all modules" () {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()
        buildFile << """
            ext.rulesInvoked = []
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

            resolve.doLast { assert rulesInvoked == [ '1.0', '1.0', '1.0', '1.0', '1.0' ] }
        """

        expect:
        succeeds 'resolve'
    }

    def "can apply all rule types by module" () {
        repo.module('org.test', 'projectA', '1.0').publish().allowAll()
        buildFile << """
            ext.rulesInvoked = []
            ext.rulesUninvoked = []
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
            }
        """

        expect:
        succeeds 'resolve'
    }

    String sq(String input) {
        return escapeForSingleQuoting(input)
    }

    String escapeForSingleQuoting(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }
}
