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
            $repo

            dependencies {
                conf 'org.stuff:foo:2.0', 'org.utils:impl:1.3', 'org.utils:optional-lib:5.0'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    if (it.requested.group == 'org.utils' && it.requested.name != 'optional-lib') {
                        it.useVersion '1.5'
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
            $repo

            dependencies {
                conf 'org.stuff:foo:2.0'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    if (it.requested.group == 'org.utils') {
                        it.useVersion '1.5'
                    }
	            }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
	            assert deps*.selected.id.name == ['foo', 'impl', 'api']
	            assert deps*.selected.id.version == ['2.0', '1.5', '1.5']
	            assert deps*.selected.selectionReason.forced         == [false, false, false]
	            assert deps*.selected.selectionReason.selectedByAction == [false, true, true]
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "all actions are executed orderly and last one wins"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $repo

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
	                assert it.target == it.requested
                    it.useVersion '1.4'
	            }
	            eachDependency {
	                assert it.target.version == '1.4'
	                assert it.target.name == it.requested.name
	                assert it.target.group == it.requested.group
                    it.useVersion '1.5'
	            }
	            eachDependency {
	                assert it.target.version == '1.5'
	                //don't change the version
	            }
	        }

	        task check << {
	            configurations.conf.incoming.resolutionResult.allDependencies {
	                assert it.selected.id.version == '1.5'
	                assert it.selected.selectionReason.selectedByAction
	                assert it.selected.selectionReason.description == 'selected by action'
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "can unforce the version"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $repo

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
                force("org.utils:impl:1.5", "org.utils:api:1.5")

	            eachDependency {
                    it.useVersion it.requested.version
	            }
	        }

	        task check << {
	            configurations.conf.incoming.resolutionResult.allDependencies {
	                assert it.selected.id.version == '1.3'
                    def reason = it.selected.selectionReason
                    assert !reason.forced
                    assert reason.selectedByAction
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "actions are applied after forced modules"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $repo

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
                force("org.utils:impl:1.5", "org.utils:api:1.5")

	            eachDependency {
                    assert it.target.version == '1.5'
                    it.useVersion '1.3'
	            }
	        }

	        task check << {
	            configurations.conf.incoming.resolutionResult.allDependencies {
	                assert it.selected.id.version == '1.3'
                    def reason = it.selected.selectionReason
                    assert !reason.forced
                    assert reason.selectedByAction
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "forced modules and actions coexist"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $repo

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
                force("org.utils:impl:1.5")

	            eachDependency {
                    if (it.requested.name == 'api') {
                        assert it.target == it.requested
                        it.useVersion '1.5'
                    }
	            }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.find {
                    it.selected.id.name == 'impl' &&
                    it.selected.id.version == '1.5' &&
                    it.selected.selectionReason.forced &&
                    !it.selected.selectionReason.selectedByAction
                }

                assert deps.find {
	                it.selected.id.name == 'api' &&
                    it.selected.id.version == '1.5' &&
                    !it.selected.selectionReason.forced &&
                    it.selected.selectionReason.selectedByAction
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "actions triggered exactly once per the same dependency"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.3').publish()

        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        mavenRepo.module("org.stuff", "foo", '2.0').dependsOn('org.utils', 'api', '1.5').publish()
        mavenRepo.module("org.stuff", "bar", '2.0').dependsOn('org.utils', 'impl', '1.3').publish()

        /*
        dependencies:

        impl:1.3->api:1.3
        foo->api:1.5
        bar->impl:1.3(*)->api:1.3(*)

        * - should be excluded as it was already visited
        */

        buildFile << """
            $repo

            dependencies {
                conf 'org.utils:impl:1.3', 'org.stuff:foo:2.0', 'org.stuff:bar:2.0'
            }

            List requested = []

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    requested << "\$it.requested.name:\$it.requested.version"
	            }
	        }

	        task check << {
                configurations.conf.resolve()
                assert requested == ['impl:1.3', 'foo:2.0', 'bar:2.0', 'api:1.3', 'api:1.5']
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "runtime exception when evaluating action yields decent exception"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.3').publish()

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $repo

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    it.useVersion '1.3' //happy
	            }
                eachDependency {
                    throw new RuntimeException("Unhappy :(")
	            }
	        }

            task resolveNow << { configurations.conf.resolve() }
"""

        when:
        def failure = runAndFail("resolveNow")

        then:
        failure.dependencyResolutionFailure
                .assertFailedConfiguration(":conf")
                .assertHasCause("Problems executing resolve action for dependency - group:org.utils, module:impl, version:1.3.")
                .assertHasCause("Unhappy :(")
                .assertFailedDependencyRequiredBy(":root:1.0")
    }

    void "configuring null version is not allowed"()
    {
        mavenRepo.module("org.utils", "api", '1.3').publish()

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $repo

            dependencies {
                conf 'org.utils:api:1.3'
            }

            configurations.conf.resolutionStrategy {
	            eachDependency {
                    it.useVersion null
	            }
	        }

            task resolveNow << { configurations.conf.resolve() }
"""

        when:
        def failure = runAndFail("resolveNow")

        then:
        failure.dependencyResolutionFailure
                .assertFailedConfiguration(":conf")
                .assertHasCause("Configuring the dependency resolve details with 'null' version is not allowed")
                .assertFailedDependencyRequiredBy(":root:1.0")
    }

    String getRepo() {
        """configurations { conf }
        repositories {
            maven { url "${mavenRepo.uri}" }
        }"""
    }
}