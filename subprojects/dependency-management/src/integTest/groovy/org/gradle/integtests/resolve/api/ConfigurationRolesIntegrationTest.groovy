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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Unroll

@FluidDependenciesResolveTest
class ConfigurationRolesIntegrationTest extends AbstractIntegrationSpec {

    @Unroll("cannot resolve a configuration with role #role at execution time")
    def "cannot resolve a configuration which is for publishing only at execution time"() {
        given:
        buildFile << """

        configurations {
            internal {
                $code
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState {
            def files = configurations.internal
            doLast {
                files.files
            }
        }

        """

        when:
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        role                      | code
        'consume or publish only' | 'canBeResolved = false'
        'bucket'                  | 'canBeResolved = false; canBeConsumed = false'

    }

    @Unroll("cannot resolve a configuration with role #role at configuration time")
    def "cannot resolve a configuration which is for publishing only at configuration time"() {
        given:
        buildFile << """

        configurations {
            internal {
                $code
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState(dependsOn: configurations.internal.files) {
        }

        """

        when:
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        role                      | code
        'consume or publish only' | 'canBeResolved = false'
        'bucket'                  | 'canBeResolved = false; canBeConsumed = false'
    }

    @ToBeFixedForConfigurationCache(because = "Uses Configuration API")
    @Unroll("cannot resolve a configuration with role #role using #method")
    def "cannot resolve a configuration which is for publishing only"() {
        given:
        buildFile << """

        configurations {
            internal {
                $role
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState {
            doLast {
                configurations.internal.$method
            }
        }

        """

        when:
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        [method, role] << [
            ['getResolvedConfiguration()', 'getBuildDependencies()', 'getIncoming().getFiles()', 'getIncoming().getResolutionResult()', 'getResolvedConfiguration()'],
            ['canBeResolved = false', 'canBeResolved = false; canBeConsumed = false']
        ].combinations()
    }

    @Unroll("cannot add a dependency on a configuration role #role")
    def "cannot add a dependency on a configuration not meant to be consumed or published"() {
        given:
        file('settings.gradle') << 'include "a", "b"'
        buildFile << """
        project(':a') {
            configurations {
                compile
            }
            dependencies {
                compile project(path: ':b', configuration: 'internal')
            }

            task check {
                def files = configurations.compile
                doLast { files.files }
            }
        }
        project(':b') {
            configurations {
                internal {
                    $code
                }
            }
        }

        """

        when:
        fails 'a:check'

        then:
        failure.assertHasCause "Selected configuration 'internal' on 'project :b' but it can't be used as a project dependency because it isn't intended for consumption by other components."

        where:
        role                    | code
        'query or resolve only' | 'canBeConsumed = false'
        'bucket'                | 'canBeResolved = false; canBeConsumed = false'
    }

    @Unroll("cannot depend on default configuration if it's not consumable (#role)")
    def "cannot depend on default configuration if it's not consumable"() {
        given:
        file('settings.gradle') << 'include "a", "b"'
        buildFile << """
        project(':a') {
            configurations {
                compile
            }
            dependencies {
                compile project(path: ':b')
            }

            task check {
                def files = configurations.compile
                doLast { files.files }
            }
        }
        project(':b') {
            configurations {
                'default' {
                    $code
                }
            }
        }

        """

        when:
        fails 'a:check'

        then:
        failure.assertHasCause "Selected configuration 'default' on 'project :b' but it can't be used as a project dependency because it isn't intended for consumption by other components."

        where:
        role                    | code
        'query or resolve only' | 'canBeConsumed = false'
        'bucket'                | 'canBeResolved = false; canBeConsumed = false'
    }

}
