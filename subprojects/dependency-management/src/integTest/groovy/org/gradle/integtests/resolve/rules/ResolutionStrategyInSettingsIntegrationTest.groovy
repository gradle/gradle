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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class ResolutionStrategyInSettingsIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "resolution strategy can be set globally in settings"() {
        repository {
            'org:module:1.0'()
        }

        settingsFile << """
            dependencyResolutionManagement {
                resolutionStrategy {
                    dependencySubstitution {
                        substitute module('org:nuclear') with module('org:module:1.0')
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'org:nuclear:2.0'
            }
        """

        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:nuclear:2.0', 'org:module:1.0') {
                    selectedByRule()
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "doesn't support failing builds")
    def "resolution strategy is overriden locally"() {
        repository {
            'org:module:1.0'()
        }

        settingsFile << """
            dependencyResolutionManagement {
                resolutionStrategy {
                    dependencySubstitution {
                        substitute module('org:nuclear') with module('org:module:1.0')
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'org:nuclear:2.0'
            }
            // Configure explicitly the resolution strategy
            // Because we do this, it overrides whatever is said in settings,
            // which implies the substitution rules
            configurations.conf.resolutionStrategy.failOnVersionConflict()
        """

        when:
        repositoryInteractions {
            'org:nuclear:2.0' {
                expectGetMetadataMissing()
            }
        }
        fails ':checkDeps'

        then:
        failureCauseContains("Could not find org:nuclear:2.0.")
    }
}
