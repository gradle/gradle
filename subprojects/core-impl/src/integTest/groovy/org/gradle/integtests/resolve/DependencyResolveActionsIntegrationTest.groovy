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
            $common

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
        run("resolveConf")

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
            $common

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
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
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
            $common

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
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.size() == 2
                deps.each {
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
            $common

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
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.size() == 2
                deps.each {
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
            $common

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
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.size() == 2
                deps.each {
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
            $common

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

    void "action selects a dynamic version"()
    {
        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.4').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:1.3'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                it.useVersion '1.+'
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 1
                assert deps[0].requested.version == '1.3'
                assert deps[0].selected.id.version == '1.5'
                assert !deps[0].selected.selectionReason.forced
                assert deps[0].selected.selectionReason.selectedByAction
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "can blacklist a version"()
    {
        mavenRepo.module("org.utils", "a",  '1.4').publish()
        mavenRepo.module("org.utils", "a",  '1.3').publish()
        mavenRepo.module("org.utils", "a",  '1.2').publish()
        mavenRepo.module("org.utils", "b", '1.3').dependsOn("org.utils", "a", "1.3").publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:a:1.2', 'org.utils:b:1.3'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                // a:1.2 is blacklisted, 1.4 should be used instead:
                if (it.requested.name == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.4'
                }
	        }

	        task check << {
                def modules = configurations.conf.incoming.resolutionResult.allModuleVersions as List
                def a = modules.find { it.id.name == 'a' }
                assert a.id.version == '1.4'
                assert a.selectionReason.conflictResolution
                assert a.selectionReason.selectedByAction
                assert !a.selectionReason.forced
                assert a.selectionReason.description == 'conflict resolution by action'
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "can blacklist a version that is not used"()
    {
        mavenRepo.module("org.utils", "a",  '1.3').publish()
        mavenRepo.module("org.utils", "a",  '1.2').publish()
        mavenRepo.module("org.utils", "b", '1.3').dependsOn("org.utils", "a", "1.3").publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:a:1.2', 'org.utils:b:1.3'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                // a:1.2 is blacklisted, 1.2.1 should be used instead:
                if (it.requested.name == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.2.1'
                }
	        }

	        task check << {
                def modules = configurations.conf.incoming.resolutionResult.allModuleVersions as List
                def a = modules.find { it.id.name == 'a' }
                assert a.id.version == '1.3'
                assert a.selectionReason.conflictResolution
                assert !a.selectionReason.selectedByAction
                assert !a.selectionReason.forced
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    def "can use custom versioning scheme"()
    {
        mavenRepo.module("org.utils", "api",  '1.3').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:default'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.version == 'default') {
                    it.useVersion '1.3'
                }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 1
                deps[0].requested.version == 'default'
                deps[0].selected.id.version == '1.3'
                deps[0].selected.selectionReason.selectedByAction
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    def "can use custom versioning scheme for transitive dependencies"()
    {
        mavenRepo.module("org.utils", "api",  '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', 'default').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.version == 'default') {
                    it.useVersion '1.3'
                }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 2
                def api = deps.find { it.requested.name == 'api' }
                api.requested.version == 'default'
                api.selected.id.version == '1.3'
                api.selected.selectionReason.selectedByAction
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "action selects unavailable version"()
    {
        mavenRepo.module("org.utils", "api", '1.3').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:1.3'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                it.useVersion '1.123.15' //does not exist
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 1
                assert deps[0].failure
                assert deps[0].failure.message.contains('1.123.15')
                assert deps[0].requested.version == '1.3'
	        }
"""

        when:
        def failure = runAndFail("check", "resolveConf")

        then:
        failure.dependencyResolutionFailure
            .assertFailedConfiguration(":conf")
            .assertHasCause("Could not find group:org.utils, module:api, version:1.123.15")
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
            $common

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

            $common

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
"""

        when:
        def failure = runAndFail("resolveConf")

        then:
        failure.dependencyResolutionFailure
                .assertFailedConfiguration(":conf")
                .assertHasCause("Problems executing resolve action for dependency - group:org.utils, module:impl, version:1.3.")
                .assertHasCause("Unhappy :(")
                .assertFailedDependencyRequiredBy(":root:1.0")
    }

    String getCommon() {
        """configurations { conf }
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }

        //resolving the configuration at the end:
        gradle.startParameter.taskNames += 'resolveConf'
        """
    }
}