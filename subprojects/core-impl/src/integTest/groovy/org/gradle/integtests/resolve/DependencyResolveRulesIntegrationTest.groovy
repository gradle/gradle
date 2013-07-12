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

import static org.gradle.util.TextUtil.toPlatformLineSeparators

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class DependencyResolveRulesIntegrationTest extends AbstractIntegrationSpec {

    void "forces multiple modules by rule"()
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

    void "module forced by rule has correct selection reason"()
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
	            def deps = getResolutionResult().allDependencies
	            assert deps*.selected.id.name == ['foo', 'impl', 'api']
	            assert deps*.selected.id.version == ['2.0', '1.5', '1.5']
	            assert deps*.selected.selectionReason.forced         == [false, false, false]
	            assert deps*.selected.selectionReason.selectedByRule == [false, true, true]
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "all rules are executed orderly and last one wins"()
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
	            def deps = getResolutionResult().allDependencies
                assert deps.size() == 2
                deps.each {
	                assert it.selected.id.version == '1.5'
	                assert it.selected.selectionReason.selectedByRule
	                assert it.selected.selectionReason.description == 'selected by rule'
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
	            def deps = getResolutionResult().allDependencies
                assert deps.size() == 2
                deps.each {
	                assert it.selected.id.version == '1.3'
                    def reason = it.selected.selectionReason
                    assert !reason.forced
                    assert reason.selectedByRule
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "rule are applied after forced modules"()
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
	            def deps = getResolutionResult().allDependencies
                assert deps.size() == 2
                deps.each {
	                assert it.selected.id.version == '1.3'
                    def reason = it.selected.selectionReason
                    assert !reason.forced
                    assert reason.selectedByRule
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "forced modules and rules coexist"()
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
                def deps = getResolutionResult().allDependencies
                assert deps.find {
                    it.selected.id.name == 'impl' &&
                    it.selected.id.version == '1.5' &&
                    it.selected.selectionReason.forced &&
                    !it.selected.selectionReason.selectedByRule
                }

                assert deps.find {
	                it.selected.id.name == 'api' &&
                    it.selected.id.version == '1.5' &&
                    !it.selected.selectionReason.forced &&
                    it.selected.selectionReason.selectedByRule
	            }
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "rule selects a dynamic version"()
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
                def deps = getResolutionResult().allDependencies as List
                assert deps.size() == 1
                assert deps[0].requested.version == '1.3'
                assert deps[0].selected.id.version == '1.5'
                assert !deps[0].selected.selectionReason.forced
                assert deps[0].selected.selectionReason.selectedByRule
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
                def modules = getResolutionResult().allModuleVersions as List
                def a = modules.find { it.id.name == 'a' }
                assert a.id.version == '1.4'
                assert a.selectionReason.conflictResolution
                assert a.selectionReason.selectedByRule
                assert !a.selectionReason.forced
                assert a.selectionReason.description == 'selected by rule and conflict resolution'
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
                def modules = getResolutionResult().allModuleVersions as List
                def a = modules.find { it.id.name == 'a' }
                assert a.id.version == '1.3'
                assert a.selectionReason.conflictResolution
                assert !a.selectionReason.selectedByRule
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
                def deps = getResolutionResult().allDependencies as List
                assert deps.size() == 1
                deps[0].requested.version == 'default'
                deps[0].selected.id.version == '1.3'
                deps[0].selected.selectionReason.selectedByRule
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
                def deps = getResolutionResult().allDependencies as List
                assert deps.size() == 2
                def api = deps.find { it.requested.name == 'api' }
                api.requested.version == 'default'
                api.selected.id.version == '1.3'
                api.selected.selectionReason.selectedByRule
	        }
"""

        when:
        run("check")

        then:
        noExceptionThrown()
    }

    void "rule selects unavailable version"()
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
                def deps = getResolutionResult().allDependencies as List
                assert deps.size() == 1
                assert deps[0].attempted.group == 'org.utils'
                assert deps[0].attempted.name == 'api'
                assert deps[0].attempted.version == '1.123.15'
                assert deps[0].attemptedReason.selectedByRule
                assert deps[0].failure.message.contains('1.123.15')
                assert deps[0].requested.version == '1.3'
	        }
"""

        when:
        def failure = runAndFail("check", "resolveConf")

        then:
        failure.assertResolutionFailure(":conf")
            .assertHasCause("Could not find org.utils:api:1.123.15")
    }

    void "rules triggered exactly once per the same dependency"()
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

    void "runtime exception when evaluating rule yields decent exception"()
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
        failure.assertResolutionFailure(":conf")
                .assertHasCause("Could not resolve org.utils:impl:1.3.")
                .assertHasCause("Unhappy :(")
                .assertFailedDependencyRequiredBy(":root:1.0")
    }

    void "can substitute module name and resolve conflict"()
    {
        mavenRepo.module("org.utils", "a",  '1.2').publish()
        mavenRepo.module("org.utils", "b",  '2.0').publish()
        mavenRepo.module("org.utils", "b",  '2.1').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:a:1.2', 'org.utils:b:2.0'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.name == 'a') {
                    it.useTarget(it.requested.group + ':b:2.1')
                }
	        }

	        task check << {
                def modules = getResolutionResult().allModuleVersions as List
                assert !modules.find { it.id.name == 'a' }
                def b = modules.find { it.id.name == 'b' }
                assert b.id.version == '2.1'
                assert b.selectionReason.conflictResolution
                assert b.selectionReason.selectedByRule
                assert !b.selectionReason.forced
                assert b.selectionReason.description == 'selected by rule and conflict resolution'
	        }
"""

        when:
        run("check", "dependencies")

        then:
        output.contains(toPlatformLineSeparators("""conf
+--- org.utils:a:1.2 -> org.utils:b:2.1
\\--- org.utils:b:2.0 -> 2.1"""))
    }

    def "can substitute module group"()
    {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "c").publish()
        //a1
        //b->a2->c

        buildFile << """
            $common

            dependencies {
                conf 'org:a:1.0', 'foo:b:1.0'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.group == 'foo') {
                    it.useTarget('org:' + it.requested.name + ':' + it.requested.version)
                }
	        }
"""

        when:
        run("dependencies")

        then:
        output.contains(toPlatformLineSeparators("""
+--- org:a:1.0 -> 2.0
|    \\--- org:c:1.0
\\--- foo:b:1.0 -> org:b:1.0
     \\--- org:a:2.0 (*)"""))
    }

    def "can substitute module group, name and version"()
    {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "c").publish()
        //a1
        //b->a2->c

        buildFile << """
            $common

            dependencies {
                conf 'org:a:1.0', 'foo:bar:baz'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.group == 'foo') {
                    it.useTarget group: 'org', name: 'b', version: '1.0'
                }
	        }
"""

        when:
        run("dependencies")

        then:
        output.contains(toPlatformLineSeparators("""
+--- org:a:1.0 -> 2.0
|    \\--- org:c:1.0
\\--- foo:bar:baz -> org:b:1.0
     \\--- org:a:2.0 (*)"""))
    }

    def "provides decent feedback when target module incorrectly specified"()
    {
        buildFile << """
            $common

            dependencies {
                conf 'org:a:1.0', 'foo:bar:baz'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                it.useTarget "foobar"
	        }
"""

        when:
        runAndFail("dependencies")

        then:
        failure.assertResolutionFailure(":conf").assertHasCause("Invalid format: 'foobar'")
    }

    def "substituted module version participates in conflict resolution"()
    {
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "b", "2.0").publish()
        mavenRepo.module("org", "b", "2.0").dependsOn("org", "c", "2.0").publish()
        mavenRepo.module("org", "c", "2.0").publish()

        buildFile << """
            $common

            dependencies {
                conf 'org:a:1.0', 'org:a:2.0'
            }

            configurations.conf.resolutionStrategy.eachDependency {
                if (it.requested.name == 'a' && it.requested.version == '1.0') {
                    it.useTarget group: 'org', name: 'c', version: '1.1'
                }
	        }
"""

        when:
        run("dependencies")

        then:
        output.contains(toPlatformLineSeparators("""
conf
+--- org:a:1.0 -> org:c:2.0
\\--- org:a:2.0
     \\--- org:b:2.0
          \\--- org:c:2.0
"""))
    }

    def "module selected by conflict resolution can be selected again in a another pass of conflict resolution"()
    {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "b", "2.5").publish()
        mavenRepo.module("org", "b", "3.0").publish()
        mavenRepo.module("org", "b", "4.0").publish()

        /*
        I agree this dependency set is awkward but it is the simplest reproducible scenario
        a:1.0
        a:2.0 -> b:2.5
        b:3.0
        b:4.0

        the conflict resolution of b:
        1st pass: b:3 vs b:4(wins)
        2nd pass: b:2.5 vs b:4(wins *again*)
        */

        buildFile << """
            $common

            dependencies {
                conf 'org:b:3.0', 'org:b:4.0', 'org:a:1.0', 'org:a:2.0'
            }

            task check << {
                def modules = getResolutionResult().allModuleVersions as List
                assert modules.find { it.id.name == 'b' && it.id.version == '4.0' && it.selectionReason.conflictResolution }
            }
"""

        expect:
        run("check")
    }

    String getCommon() {
        """
        configurations {
            conf {
                incoming.withResolutionResult { project.ext.theResult = it }
            }
        }
        ResolutionResult getResolutionResult() {
            configurations.conf.resolvedConfiguration //resolve w/o artifact download
            theResult
        }
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        task resolveConf << { configurations.conf.files }

        //resolving the configuration at the end:
        gradle.startParameter.taskNames += 'resolveConf'
        """
    }
}