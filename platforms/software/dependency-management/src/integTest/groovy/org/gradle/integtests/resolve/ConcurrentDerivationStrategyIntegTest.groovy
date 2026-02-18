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
import spock.lang.Issue
import spock.lang.Unroll

class ConcurrentDerivationStrategyIntegTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/13555")
    @Unroll("consistent resolution using rules=#displayName")
    // If this test becomes flaky it means we broke the code which prevents mutation of in-memory cached module metadata
    def "selected variants are consistent using concurrent resolution of graphs from cache having different derivation strategies"() {
        executer.requireOwnGradleUserHomeDir()
        settingsFile << """
            include 'app'
            include 'lib'
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """

        def common = """
            dependencies {
                components {
                    $rules
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
            $common

            configurations {
               foo
            }

            dependencies {
               foo 'org.apache.commons:commons-lang3:3.3.1'
               foo 'org.springframework.boot:spring-boot-starter-web:2.2.2.RELEASE'
            }

            ${getResolveTask("foo", "default")}
        """
        file("lib/build.gradle") << """
            plugins {
               id 'java-library'
            }

            $common

            dependencies {
               api 'org.apache.commons:commons-lang3:3.3.1'
               implementation 'org.springframework.boot:spring-boot-starter-web:2.2.2.RELEASE'
            }

            ${getResolveTask("compileClasspath", "compile")}  
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

    def static getResolveTask(String configuration, String displayName) {
        """
            tasks.register("resolve") {
               def resolutionRoot = configurations.${configuration}.incoming.resolutionResult.getRootComponent()
               doLast {
                  Util.getAllComponents(resolutionRoot.get()).forEach {res -> 
                        assert res instanceof ResolvedComponentResult
                        if (res.id instanceof ModuleComponentIdentifier) {
                            res.variants.each {
                                println "\${res.id} -> \${it.displayName}"
                                if (it.displayName != '$displayName') {
                                    throw new AssertionError("Unexpected resolved variant \$it")
                                }
                            }
                        }
                    }
               }
            }

            class Util {
                static Set<ResolvedComponentResult> getAllComponents(ResolvedComponentResult root) {
                    final Set<ResolvedComponentResult> out = new LinkedHashSet<>();
                    eachElement(root, out);
                    return out;
                }
                
                static void eachElement(ResolvedComponentResult root, Set<ResolvedComponentResult> visited) {
                    if (!visited.add(root)) {
                        return;
                    }
                    for (DependencyResult d : root.getDependencies()) {
                        if (d instanceof ResolvedDependencyResult) {
                            eachElement(((ResolvedDependencyResult) d).getSelected(), visited);
                        }
                    }
                }
            }
        """
        // TODO: the utility methods is basically missing API, which we need to add: https://github.com/gradle/gradle/issues/26897
    }
}
