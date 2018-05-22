/*
 * Copyright 2018 the original author or authors.
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

class ComponentMetadataRulesCachingIntegrationTest extends AbstractModuleDependencyResolveTest implements ComponentMetadataRulesSupport {
    String getDefaultStatus() {
        GradleMetadataResolveRunner.useIvy()?'integration':'release'
    }

    def setup() {
        buildFile << """
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

    def "rule is cached across builds"() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

@CacheableRule
class CachedRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println 'Rule executed'
    }
}

dependencies {
    components {
        all(CachedRule)
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
        outputContains('Rule executed')


        then:
        succeeds 'resolve'
        outputDoesNotContain('Rule executed')
    }

    def 'rule cache properly differentiates inputs'() {
        repository {
            'org.test:projectA:1.0'()
        }
        buildFile << """

@CacheableRule
class CachedRuleA implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println 'Rule A executed'
            context.details.changing = true
    }
}

@CacheableRule
class CachedRuleB implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
            println 'Rule B executed - saw changing ' + context.details.changing
    }
}

dependencies {
    components {
        if (project.hasProperty('cacheA')) {
            all(CachedRuleA)
        }
        all(CachedRuleB)
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
        outputContains('Rule B executed - saw changing false')


        then:
        succeeds 'resolve', '-PcacheA'
        outputContains('Rule A executed')
        outputContains('Rule B executed - saw changing true')
    }

}
