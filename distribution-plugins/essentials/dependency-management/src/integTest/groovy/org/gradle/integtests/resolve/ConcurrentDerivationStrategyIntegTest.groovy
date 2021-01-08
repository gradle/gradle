/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue
import spock.lang.Unroll

class ConcurrentDerivationStrategyIntegTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache
    @Issue("https://github.com/gradle/gradle/issues/13555")
    @Unroll("consistent resolution using rules=#displayName")
    // If this test becomes flaky it means we broke the code which prevents mutation of in-memory cached module metadata
    def "selected variants are consistent using concurrent resolution of graphs from cache having different derivation strategies"() {
        executer.requireOwnGradleUserHomeDir()
        settingsFile << """
            include 'app'
            include 'lib'
        """

        buildFile << """
            subprojects {
                ${mavenCentralRepository()}
                dependencies {
                    components {
                        $rules
                    }
                }
            }

            class NonCachedRule implements ComponentMetadataRule {
                @Override
                void execute(ComponentMetadataContext context) {
                    println("Applying rule on \$context.details.id")
                }
            }

            @CacheableRule
            class CachedRule implements ComponentMetadataRule {
                @Override
                void execute(ComponentMetadataContext context) {
                    println("Applying rule on \$context.details.id")
                }
            }
        """

        file('app/build.gradle') << """
            configurations {
               foo
            }

            dependencies {
               foo 'org.apache.commons:commons-lang3:3.3.1'
               foo 'org.springframework.boot:spring-boot-starter-web:2.2.2.RELEASE'
            }

            tasks.register("resolve") {
               doLast {
                  configurations.foo.incoming.resolutionResult.allComponents {
                      assert it instanceof ResolvedComponentResult
                      if (id instanceof ModuleComponentIdentifier) {
                          variants.each {
                              println "\$id -> \${it.displayName}"
                              if (it.displayName != 'default') {
                                  throw new AssertionError("Unexpected resolved variant \$it")
                              }
                          }
                      }
                  }
               }
            }
        """
        file("lib/build.gradle") << """
            plugins {
               id 'java-library'
            }

            dependencies {
               api 'org.apache.commons:commons-lang3:3.3.1'
               implementation 'org.springframework.boot:spring-boot-starter-web:2.2.2.RELEASE'
            }

            tasks.register("resolve") {
               doLast {
                  configurations.compileClasspath.incoming.resolutionResult.allComponents {
                      assert it instanceof ResolvedComponentResult
                      if (id instanceof ModuleComponentIdentifier) {
                          variants.each {
                              println "\$id -> \${it.displayName}"
                              if (it.displayName != 'compile') {
                                  throw new AssertionError("Unexpected resolved variant \$it")
                              }
                          }
                      }
                  }
               }
            }
        """

        when:
        executer.withArgument('--parallel')
        run 'resolve'

        then:
        noExceptionThrown()

        when: "second build from cache"
        executer.withArgument('--parallel')
        run 'resolve'

        then:
        noExceptionThrown()

        where:
        displayName       | rules
        "no rules"        | ""
        "non-cached rule" | "all(NonCachedRule)"
        "cached rule"     | "all(CachedRule)"
    }
}
