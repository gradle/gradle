/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class CachedMetadataRuleSoakTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/34691")
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "rule is cached across builds"() {
        executer.requireOwnGradleUserHomeDir("cannot reuse cross-build caches")
        buildFile << """
        plugins {
            id 'java'
        }
        ${mavenCentralRepository()}

        configurations {
            conf
        }

        dependencies {
            conf 'log4j:log4j:1.2.17'
        }

        task resolve(type: Sync) {
            from configurations.conf
            into 'libs'
        }

        @CacheableRule
        class CachedRule implements ComponentMetadataRule {
            public void execute(ComponentMetadataContext context) {
                    println "Rule executed on \${context.details.id}"
                    context.details.allVariants {
                        println("Variant \$it")
                        withDependencies { deps ->
                            deps.each {
                               println "See dependency: \$it"
                            }
                        }
                    }
            }
        }

        // ~750MB
        ext.memoryHog = new byte[1024*1024*750]

        dependencies {
            components {
                all(CachedRule)
            }
        }
        """

        expect:
        succeeds 'resolve'
        outputContains('Rule executed')
        outputContains('See dependency')

        succeeds 'resolve'
        outputDoesNotContain('Rule executed')
        outputDoesNotContain('See dependency')
    }
}
