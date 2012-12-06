/*
 * Copyright 2012 the original author or authors.
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

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class DependencyResolveActionsIntegrationTest extends AbstractIntegrationSpec {

    void "forces multiple modules by action"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        mavenRepo.module("org.stuff", "foo", '2.0').dependsOn('org.utils', 'api', '1.5') publish()
        mavenRepo.module("org.utils", "optional-lib", '5.0').publish()

        //above models the scenario where org.utils:api and org.utils:impl are libraries that must be resolved with the same version
        //however due to the conflict resolution, org.utils:api:1.5 and org.utils.impl:1.3 are resolved.

        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.stuff:foo:2.0', 'org.utils:impl:1.3', 'org.utils:optional-lib:5.0'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    if (it.requested.group == 'org.utils' && it.requested.name != 'optional-lib') {
                        it.forceVersion '1.5'
                    }
	            }
	            failOnVersionConflict()
	        }
"""

        when:
        run("dependencies")

        then:
        noExceptionThrown()
    }

    void "module forced by action has correct selection reason"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        mavenRepo.module("org.stuff", "foo", '2.0').dependsOn('org.utils', 'impl', '1.3') publish()

        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.stuff:foo:2.0'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    if (it.requested.group == 'org.utils') {
                        it.forceVersion '1.5'
                    }
	            }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
	            assert deps*.selected.id.name == ['foo', 'impl', 'api']
	            assert deps*.selected.id.version == ['2.0', '1.5', '1.5']
	            assert deps*.selected.selectionReason.forced == [false, true, true]
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "all actions are executed and last one wins"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
	                assert it.forcedVersion == null
                    it.forceVersion '1.4'
	            }
	            eachDependency {
	                assert it.forcedVersion == '1.4'
                    it.forceVersion '1.5'
	            }
	            eachDependency {
	                assert it.forcedVersion == '1.5'
	                //leave the forced version intact
	            }
	        }

	        task check << {
	            configurations.conf.incoming.resolutionResult.allDependencies {
	                assert it.selected.id.version == '1.5'
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }
}