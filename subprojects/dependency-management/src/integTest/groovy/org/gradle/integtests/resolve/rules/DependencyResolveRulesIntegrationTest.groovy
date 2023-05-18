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


package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

import java.util.concurrent.CopyOnWriteArrayList

@FluidDependenciesResolveTest
class DependencyResolveRulesIntegrationTest extends AbstractIntegrationSpec {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile)

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    /**
     * Test demonstrating current (not necessarily desired) behaviour
     */
    void "can replace project dependency with external dependency"() {
        mavenRepo.module("org.gradle.test", "a", '1.3').publish()

        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                apply plugin: 'base'
                group = 'org.gradle.test'
                version = '1.2'
                configurations { 'default' }
            }

            project(':b') {
                $common
                dependencies {
                    conf project(':a')
                }
                configurations.conf.resolutionStrategy {
                    eachDependency {
                        assert it.requested.toString() == 'org.gradle.test:a:1.2'
                        assert it.target.toString() == 'org.gradle.test:a:1.2'
                        it.useVersion('1.3')
                        assert it.target.toString() == 'org.gradle.test:a:1.3'
                    }
                }
            }
"""
        resolve.prepare("conf")

        expect:
        succeeds(":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                edge("project :a", "org.gradle.test:a:1.3") {
                    selectedByRule()
                }
            }
        }
    }

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
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                 module("org.stuff:foo:2.0") {
                    module("org.utils:api:1.5") {
                        selectedByRule()
                    }
                }
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    selectedByRule()
                    module("org.utils:api:1.5")
                }
                module("org.utils:optional-lib:5.0")
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.stuff:foo:2.0") {
                    edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                        selectedByRule()
                        module("org.utils:api:1.5") {
                            selectedByRule()
                        }
                    }
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    selectedByRule()
                    module("org.utils:api:1.5") {
                        selectedByRule()
                    }
                }
            }
        }
    }

    void "can override forced version with rule"()
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.utils:impl:1.3") {
                    selectedByRule()
                    forced()
                    module("org.utils:api:1.3") {
                        selectedByRule()
                        forced()
                    }
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.utils:impl:1.3") {
                    selectedByRule()
                    forced()
                    module("org.utils:api:1.3") {
                        selectedByRule()
                        forced()
                    }
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    forced()
                    module("org.utils:api:1.5") {
                        selectedByRule()
                    }
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:api:1.3", "org.utils:api:1.5") {
                    selectedByRule()
                }
            }
        }
    }

    void "can deny a version"()
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
                // a:1.2 is denied, 1.4 should be used instead:
                if (it.requested.name == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.4'
                }
            }
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:a:1.2", "org.utils:a:1.4") {
                    selectedByRule()
                    byConflictResolution("between versions 1.4 and 1.3")
                }
                module("org.utils:b:1.3") {
                    edge("org.utils:a:1.3", "org.utils:a:1.4")
                }
            }
        }
    }

    void "can deny a version that is not used"()
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
                // a:1.2 is denied, 1.2.1 should be used instead:
                if (it.requested.name == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.2.1'
                }
            }
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:a:1.2", "org.utils:a:1.3") {
                    selectedByRule()
                    byConflictResolution("between versions 1.3 and 1.2.1")
                }
                module("org.utils:b:1.3") {
                    module("org.utils:a:1.3")
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:api:default", "org.utils:api:1.3") {
                    selectedByRule()
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.utils:impl:1.3") {
                    edge("org.utils:api:default", "org.utils:api:1.3") {
                        selectedByRule()
                    }
                }
            }
        }
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

            task check {
                doLast {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0].attempted.group == 'org.utils'
                    assert deps[0].attempted.module == 'api'
                    assert deps[0].attempted.version == '1.123.15'
                    assert deps[0].attemptedReason.selectedByRule
                    assert deps[0].failure.message.contains('1.123.15')
                    assert deps[0].requested.version == '1.3'
                }
            }
"""

        when:
        def failure = runAndFail("check", "resolveConf")

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("Could not find org.utils:api:1.123.15")
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

            List requested = new ${CopyOnWriteArrayList.name}()

            configurations.conf.resolutionStrategy {
                eachDependency {
                    requested << "\$it.requested.name:\$it.requested.version"
                }
            }

            task check {
                def files = configurations.conf
                doLast {
                    files.files
                    requested = requested.sort()
                    assert requested == ['api:1.3', 'api:1.5', 'bar:2.0', 'foo:2.0', 'impl:1.3']
                }
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
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("""Could not resolve org.utils:impl:1.3.
Required by:
    project :""")
        failure.assertHasCause("Unhappy :(")
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.utils:a:1.2", "org.utils:b:2.1") {
                    selectedByRule()
                    byConflictResolution("between versions 2.1 and 2.0")
                }
                edge("org.utils:b:2.0", "org.utils:b:2.1")
            }
        }
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
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:c:1.0")
                }
                edge("foo:b:1.0", "org:b:1.0") {
                    selectedByRule()
                    module("org:a:2.0")
                }
            }
        }
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
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:c:1.0")
                }
                edge("foo:bar:baz", "org:b:1.0") {
                    selectedByRule()
                    module("org:a:2.0")
                }
            }
        }
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
        runAndFail("dependencies", "resolveConf")

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("Could not resolve org:a:1.0.")
        failure.assertHasCause("Invalid format: 'foobar'")
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
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:c:2.0") {
                    selectedByRule()
                    byConflictResolution("between versions 2.0 and 1.1")
                }
                module("org:a:2.0") {
                    module("org:b:2.0") {
                        module("org:c:2.0")
                    }
                }
            }
        }
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
"""
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:b:3.0", "org:b:4.0")
                module("org:b:4.0") {
                    byConflictResolution("between versions 4.0, 3.0 and 2.5")
                }
                edge("org:a:1.0", "org:a:2.0")
                module("org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    edge("org:b:2.5", "org:b:4.0")
                }
            }
        }
    }

    def "custom selection reasons are available in resolution result"() {
        given:
        mavenRepo.module("org", "foo", "1.0").publish()
        mavenRepo.module("org", "foo", "2.0").publish()
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org.test", "bar", "2.0").publish()
        mavenRepo.module("org", "baz", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.eachDependency {
                        switch (it.requested.name) {
                           case 'foo':
                              it.because('because I am in control').useVersion('2.0')
                              break
                           case 'bar':
                              it.because('why not?').useTarget('org.test:bar:2.0')
                              break
                           default:
                              useVersion(it.requested.version)
                        }
                    }
                }
            }
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:bar:1.0'
                conf 'org:baz:1.0'
            }
        """
        resolve.prepare("conf")

        expect:
        succeeds("checkDeps")
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:1.0", "org:foo:2.0") {
                    selectedByRule("because I am in control")
                }
                edge("org:bar:1.0", "org.test:bar:2.0") {
                    selectedByRule("why not?")
                }
                module("org:baz:1.0") {
                    selectedByRule()
                }
            }
        }

        when:
        run "check"

        then:
        noExceptionThrown()
    }

    String getCommon() {
        """configurations { conf }
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        task resolveConf {
            def files = configurations.conf
            doLast { files.files }
        }
        """
    }
}
