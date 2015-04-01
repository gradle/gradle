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
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators

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
                dependencySubstitution {
                    eachModule {
                        if (it.requested.group == 'org.utils' && it.requested.module != 'optional-lib') {
                            it.useVersion '1.5'
                        }
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
                dependencySubstitution {
                    eachModule {
                        if (it.requested.group == 'org.utils') {
                            it.useVersion '1.5'
                        }
                    }
                }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
	            assert deps*.selected.id.module == ['foo', 'impl', 'api']
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

    @Ignore("Deprecation not yet added")
    void "warns about using deprecated resolution rules"()
    {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:1.3'
            }

            configurations.conf.resolutionStrategy {
                eachDependency {
                    it.useVersion "1.5"
                }
	        }
"""

        executer.withDeprecationChecksDisabled()

        when:
        succeeds()

        then:
        output.contains("The ResolutionStrategy.eachDependency() method has been deprecated and is scheduled to be removed in Gradle 3.0. Please use the DependencySubstitution.eachModule() method instead.")
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
                dependencySubstitution {
                    eachModule {
                        assert it.target == it.requested
                        it.useVersion '1.4'
                    }
                    eachModule {
                        assert it.target.version == '1.4'
                        assert it.target.module == it.requested.module
                        assert it.target.group == it.requested.group
                        it.useVersion '1.5'
                    }
                    eachModule {
                        assert it.target.version == '1.5'
                        //don't change the version
                    }
                }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
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

    void "all rules are executed orderly and last one wins, including deprecated resolution rules"()
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
                dependencySubstitution {
                    eachModule {
                        assert it.target == it.requested
                        it.useVersion '1.4'
                    }
                }
                eachDependency {
                    assert it.target.version == '1.4'
                    assert it.target.module == it.requested.name
                    assert it.target.group == it.requested.group
                    it.useVersion '1.5'
                }
                dependencySubstitution {
                    eachDependency {
                        assert it.target.version == '1.5'
                        //don't change the version
                    }
                }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.size() == 2
                deps.each {
	                assert it.selected.id.version == '1.5'
	                assert it.selected.selectionReason.selectedByRule
	                assert it.selected.selectionReason.description == 'selected by rule'
	            }
	        }
"""
        executer.withDeprecationChecksDisabled()

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

                dependencySubstitution {
    	            eachModule {
                        it.useVersion it.requested.version
	                }
                }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
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

                dependencySubstitution {
                    eachModule {
                        assert it.target.version == '1.5'
                        it.useVersion '1.3'
                    }
                }
	        }

	        task check << {
	            def deps = configurations.conf.incoming.resolutionResult.allDependencies
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

                dependencySubstitution {
                    withModule("org.utils:api") {
                        assert it.target == it.requested
                        it.useVersion '1.5'
                    }
                }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.find {
                    it.selected.id.module == 'impl' &&
                    it.selected.id.version == '1.5' &&
                    it.selected.selectionReason.forced &&
                    !it.selected.selectionReason.selectedByRule
                }

                assert deps.find {
	                it.selected.id.module == 'api' &&
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                it.useVersion '1.+'
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
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

    void "can replace external dependency with project dependency"()
    {
        settingsFile << 'include "api", "impl"'
        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf group: "org.utils", name: "api", version: "1.5", configuration: "conf"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule(group: "org.utils", name: "api") {
                    it.useTarget project(":api")
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                    assert deps[0].selected.componentId == projectId(":api")

                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can access built artifacts from substituted project dependency"()
    {
        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":api") {
                task build << {
                    mkdir(projectDir)
                    file("artifact.txt") << "Lajos"
                }

                artifacts {
                    conf (file("artifact.txt")) {
                        builtBy build
                    }
                }
            }

            project(":impl") {
                dependencies {
                    conf group: "org.utils", name: "api", version: "1.5"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule(group: "org.utils", name: "api") {
                    it.useTarget project(":api")
                }

                task check(dependsOn: configurations.conf) << {
                    def files = configurations.conf.files
                    assert files*.name.sort() == ["artifact.txt"]
                    assert files*.text.sort() == ["Lajos"]
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can replace project dependency with external dependency"()
    {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf project(path: ":api", configuration: "default")
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withProject(":api") {
                    it.useTarget group: "org.utils", name: "api", version: "1.5"
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(projectId(":api"))
                    assert deps[0].selected.componentId == moduleId("org.utils", "api", "1.5")

                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can replace transitive external dependency with project dependency"()
    {
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()
        settingsFile << 'include "api", "test"'

        buildFile << """
            $common

            project(":test") {
                dependencies {
                    conf group: "org.utils", name: "impl", version: "1.5"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule("org.utils:api") {
                    it.useTarget project(":api")
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 2
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.selected.componentId == moduleId("org.utils", "impl", "1.5")
                        !it.selected.selectionReason.forced &&
                        !it.selected.selectionReason.selectedByRule
                    }
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                        it.selected.componentId == projectId(":api")
                        !it.selected.selectionReason.forced &&
                        it.selected.selectionReason.selectedByRule
                    }
                }
            }
"""

        expect:
        succeeds("test:check")
    }

    void "can replace client module dependency with project dependency"()
    {
        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf module(group: "org.utils", name: "api", version: "1.5")
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule("org.utils:api") {
                    it.useTarget project(":api")
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                    assert deps[0].selected.componentId == projectId(":api")

                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can replace client module's transitive dependency with project dependency"()
    {
        settingsFile << 'include "api", "impl"'
        mavenRepo.module("org.utils", "bela", '1.5').publish()

        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf module(group: "org.utils", name: "bela", version: "1.5") {
                        dependencies group: "org.utils", name: "api", version: "1.5"
                    }
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule("org.utils:api") {
                    it.useTarget project(":api")
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 2
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.selected.componentId == moduleId("org.utils", "bela", "1.5") &&
                        !it.selected.selectionReason.forced &&
                        !it.selected.selectionReason.selectedByRule
                    }
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "api", "1.5")) &&
                        it.selected.componentId == projectId(":api") &&
                        !it.selected.selectionReason.forced &&
                        it.selected.selectionReason.selectedByRule
                    }
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can replace external dependency declared in extended configuration with project dependency"()
    {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":impl") {
                configurations {
                    testConf.extendsFrom conf
                }

                dependencies {
                    conf group: "org.utils", name: "api", version: "1.5"
                }

                configurations.testConf.resolutionStrategy.dependencySubstitution.withModule("org.utils:api") {
                    it.useTarget project(":api")
                }

                task checkConf << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                    assert deps[0].selected.componentId == moduleId("org.utils", "api", "1.5")

                    assert !deps[0].selected.selectionReason.forced
                    assert !deps[0].selected.selectionReason.selectedByRule
                }

                task checkTestConf << {
                    def deps = configurations.testConf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                    assert deps[0].selected.componentId == projectId(":api")

                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }

                task check(dependsOn: [ checkConf, checkTestConf ])
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "can replace forced external dependency with project dependency"()
    {
        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf group: "org.utils", name: "api", version: "1.5"
                }

                configurations.conf.resolutionStrategy {
                    force("org.utils:api:1.3")

                    dependencySubstitution.withModule("org.utils:api") {
                        it.useTarget project(":api")
                    }
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult

                    assert deps[0].requested.matchesStrictly(moduleId("org.utils", "api", "1.5"))
                    assert deps[0].selected.componentId == projectId(":api")

                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "replacing external module dependency with project dependency keeps the original configuration"()
    {
        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":api") {
                configurations {
                    archives
                }

                artifacts {
                    archives file("archives.txt")
                    conf file("conf.txt")
                }
            }

            project(":impl") {
                configurations {
                    compile
                }

                dependencies {
                    compile group: "org.utils", name: "api", version: "1.5", configuration: "conf"
                }

                configurations.compile.resolutionStrategy.dependencySubstitution.withModule("org.utils:api") {
                    it.useTarget project(":api")
                }

                task check << {
                    def files = configurations.compile.files
                    assert files*.name.sort() == ["conf.txt"]
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    void "replacing external module dependency with project dependency keeps the original transitivity"()
    {
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()
        settingsFile << 'include "impl", "test"'

        buildFile << """
            $common

            project(":test") {
                dependencies {
                    conf (group: "org.utils", name: "impl", version: "1.5") { transitive = false }
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withModule("org.utils:impl") {
                    it.useTarget project(":impl")
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "impl", "1.5"))
                        it.selected.componentId == projectId(":impl")
                        !it.selected.selectionReason.forced &&
                        it.selected.selectionReason.selectedByRule
                    }
                }
            }
"""

        expect:
        succeeds("test:check")
    }

    void "external dependency substituted for a project dependency participates in conflict resolution"()
    {
        mavenRepo.module("org.utils", "api", '2.0').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf project(":api")
                    conf "org.utils:api:2.0"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.withProject(":api") {
                    it.useTarget "org.utils:api:1.6"
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 2
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(projectId(":api")) &&
                        it.selected.componentId == moduleId("org.utils", "api", "2.0") &&
                        !it.selected.selectionReason.forced &&
                        !it.selected.selectionReason.selectedByRule &&
                        it.selected.selectionReason.conflictResolution
                    }
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "api", "2.0")) &&
                        it.selected.componentId == moduleId("org.utils", "api", "2.0") &&
                        !it.selected.selectionReason.forced &&
                        !it.selected.selectionReason.selectedByRule &&
                        it.selected.selectionReason.conflictResolution
                    }

                    def resolvedDeps = configurations.conf.resolvedConfiguration.firstLevelModuleDependencies
                    resolvedDeps.size() == 1
                    resolvedDeps[0].module.id == moduleId("org.utils", "api", "2.0")
                }
            }
"""

        expect:
        succeeds("impl:check")
    }

    @Unroll
    void "project dependency substituted for an external dependency participates in conflict resolution (version #apiProjectVersion)"()
    {
        mavenRepo.module("org.utils", "api", '2.0').publish()
        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common

            project(":api") {
                group "org.utils"
                version = $apiProjectVersion
            }

            project(":impl") {
                dependencies {
                    conf "org.utils:api:1.5"
                    conf "org.utils:api:2.0"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                    if (it.requested.module == "api" && it.requested.version == "1.5") {
                        it.useTarget project(":api")
                    }
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 2
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "api", "1.5")) &&
                        it.selected.componentId == $winner &&
                        !it.selected.selectionReason.forced &&
                        it.selected.selectionReason.selectedByRule == $selectedByRule &&
                        it.selected.selectionReason.conflictResolution
                    }
                    assert deps.find {
                        it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult &&
                        it.requested.matchesStrictly(moduleId("org.utils", "api", "2.0")) &&
                        it.selected.componentId == $winner &&
                        !it.selected.selectionReason.forced &&
                        it.selected.selectionReason.selectedByRule == $selectedByRule &&
                        it.selected.selectionReason.conflictResolution
                    }
                }
            }
"""

        expect:
        succeeds("impl:check")

        where:
        apiProjectVersion | winner                                | selectedByRule
        "1.6"             | 'moduleId("org.utils", "api", "2.0")' | false
        "3.0"             | 'projectId(":api")'                   | true
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                // a:1.2 is blacklisted, 1.4 should be used instead:
                if (it.requested.module == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.4'
                }
	        }

	        task check << {
                def modules = configurations.conf.incoming.resolutionResult.allComponents.findAll { it.id instanceof ModuleComponentIdentifier } as List
                def a = modules.find { it.id.module == 'a' }
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                // a:1.2 is blacklisted, 1.2.1 should be used instead:
                if (it.requested.module == 'a' && it.requested.version == '1.2') {
                    it.useVersion '1.2.1'
                }
	        }

	        task check << {
                def modules = configurations.conf.incoming.resolutionResult.allComponents.findAll { it.id instanceof ModuleComponentIdentifier } as List
                def a = modules.find { it.id.module == 'a' }
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                if (it.requested.version == 'default') {
                    it.useVersion '1.3'
                }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                if (it.requested.version == 'default') {
                    it.useVersion '1.3'
                }
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 2
                def api = deps.find { it.requested.module == 'api' }
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                it.useVersion '1.123.15' //does not exist
	        }

	        task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                assert deps.size() == 1
                assert deps[0].attempted.group == 'org.utils'
                assert deps[0].attempted.module == 'api'
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
                dependencySubstitution {
                    eachModule {
                        requested << "\$it.requested.module:\$it.requested.version"
                    }
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
                dependencySubstitution {
                    eachModule {
                        it.useVersion '1.3' //happy
                    }
                    eachModule {
                        throw new RuntimeException("Unhappy :(")
                    }
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

            configurations.conf.resolutionStrategy.dependencySubstitution.withModule("org.utils:a") {
                it.useTarget(it.requested.group + ':b:2.1')
	        }

	        task check << {
                def modules = configurations.conf.incoming.resolutionResult.allComponents.findAll { it.id instanceof ModuleComponentIdentifier } as List
                assert !modules.find { it.id.module == 'a' }
                def b = modules.find { it.id.module == 'b' }
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                if (it.requested.group == 'foo') {
                    it.useTarget('org:' + it.requested.module + ':' + it.requested.version)
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
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

            configurations.conf.resolutionStrategy.dependencySubstitution.eachModule {
                if (it.requested.module == 'a' && it.requested.version == '1.0') {
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
                def modules = configurations.conf.incoming.resolutionResult.allComponents.findAll { it.id instanceof ModuleComponentIdentifier } as List
                assert modules.find { it.id.module == 'b' && it.id.version == '4.0' && it.selectionReason.conflictResolution }
            }
"""

        expect:
        run("check")
    }

    String getCommon() {
        """
        allprojects {
            configurations {
                conf
            }
            configurations.create("default").extendsFrom(configurations.conf)

            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            task resolveConf << { configurations.conf.files }
        }

        //resolving the configuration at the end:
        gradle.startParameter.taskNames += 'resolveConf'

        def moduleId(String group, String name, String version) {
            return org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(group, name, version)
        }

        def projectId(String projectPath) {
            return org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newId(projectPath)
        }
        """
    }
}
