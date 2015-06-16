/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.util.TextUtil
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DependencySubstitutionRulesIntegrationTest extends AbstractIntegrationSpec {

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
                    all {
                        if (it.requested instanceof ModuleComponentSelector) {
                            if (it.requested.group == 'org.utils' && it.requested.module != 'optional-lib') {
                                it.useTarget group: 'org.utils', name: it.requested.module, version: '1.5'
                            }
                        }
                    }
                }
                failOnVersionConflict()
            }
"""

        expect:
        succeeds "resolveConf"
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
                    all {
                        if (it.requested.group == 'org.utils') {
                            it.useTarget group: 'org.utils', name: it.requested.module, version: '1.5'
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

        expect:
        succeeds "check"
    }

    void "all rules are executed in order and last one wins"()
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
                    all {
                        assert it.target == it.requested
                        it.useTarget group: it.requested.group, name: it.requested.module, version: '1.4'
                    }
                    all {
                        assert it.target.version == '1.4'
                        assert it.target.module == it.requested.module
                        assert it.target.group == it.requested.group
                        it.useTarget group: it.requested.group, name: it.requested.module, version: '1.5'
                    }
                    all {
                        assert it.target.version == '1.5'
                        //don't change the version
                    }
                }
            }

            task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies
                assert deps.size() == 2

                def apiDep = deps.find({ it.selected.id.module == 'api' }).selected
                assert apiDep.id.version == '1.5'
                assert !apiDep.selectionReason.forced
                assert apiDep.selectionReason.selectedByRule
                assert apiDep.selectionReason.description == 'selected by rule'
            }
"""

        expect:
        succeeds "check"
    }

    void "all rules are executed in order and last one wins, including resolution rules"()
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
                    all {
                        assert it.target == it.requested
                        it.useTarget group: 'org.utils', name: it.requested.module, version: '1.4'
                    }
                }
                eachDependency {
                    assert it.target.version == '1.4'
                    assert it.target.name == it.requested.name
                    assert it.target.group == it.requested.group
                    it.useVersion '1.5'
                }
                dependencySubstitution {
                    all {
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

        expect:
        succeeds "check"
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
                    all {
                        it.useTarget it.requested
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

        expect:
        succeeds "check"
    }

    void "forced modules and rules coexist"()
    {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.6').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
                force("org.utils:impl:1.5")

                dependencySubstitution {
                    substitute module("org.utils:api") with module("org.utils:api:1.6")
                }
            }

            task check << {
                def deps = configurations.conf.incoming.resolutionResult.allDependencies

                def apiDep = deps.find({ it.selected.id.module == 'api' }).selected
                assert apiDep.id.version == '1.6'
                assert !apiDep.selectionReason.forced
                assert apiDep.selectionReason.selectedByRule

                def implDep = deps.find({ it.selected.id.module == 'impl' }).selected
                assert implDep.id.version == '1.5'
                assert implDep.selectionReason.forced
                assert !implDep.selectionReason.selectedByRule
            }
"""

        expect:
        succeeds "check"
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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:api:1.3') with module('org.utils:api:1.+')
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

        expect:
        succeeds "check"
    }

    void "can substitute modules with project dependency using #name"()
    {
        settingsFile << 'include "api", "impl"'
        buildFile << """
            $common

            project(":impl") {
                dependencies {
                    conf group: "org.utils", name: "api", version: "1.5", configuration: "conf"
                }

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("$selector") with project(":api")
                }

                task check(dependsOn: configurations.conf) << {
                    assert configurations.conf.collect { it.name } == ['api.jar']

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

        when:
        succeeds "impl:check"

        then:
        executedAndNotSkipped ":api:jar"

        where:
        name                 | selector
        "matching module"    | "org.utils:api"
        "matching component" | "org.utils:api:1.5"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api") with project(":api")
                }

                task check(dependsOn: configurations.conf) << {
                    def files = configurations.conf.files
                    assert files*.name.sort() == ["api.jar", "artifact.txt"]
                    assert files[1].text == "Lajos"
                }
            }
"""

        when:
        succeeds ":impl:check"

        then:
        executedAndNotSkipped ":api:build"
    }

    @Unroll
    void "can replace project dependency #projectGroup:api:#projectVersion with external dependency org.utils:api:1.5"()
    {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << """
            $common
            project(":api") {
                group = "$projectGroup"
                version = "$projectVersion"
            }
            project(":impl") {
                dependencies {
                    conf project(path: ":api", configuration: "default")
                }

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute project(":api") with module("org.utils:api:1.5")
                }

                task check(dependsOn: configurations.conf) << {
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

        when:
        succeeds "impl:check"

        then:
        notExecuted ":api:jar"

        where:
        projectVersion | projectGroup | scenario
        "1.5"          | "org.utils"  | "the same as the external dependency"
        "2.0"          | "org.utils"  | "GAV different, version only"
        "1.5"          | "my.org.utils"  | "GAV different, group only"
        "2.0"          | "my.org.utils"  | "GAV different, version and group"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api") with project(":api")
                }

                task check(dependsOn: configurations.conf) << {
                    assert configurations.conf.collect { it.name } == ['impl-1.5.jar', 'api.jar']

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

        when:
        succeeds "test:check"

        then:
        executedAndNotSkipped ":api:jar"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api") with project(":api")
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
        succeeds "impl:check"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api") with project(":api")
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
        succeeds "impl:check"
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

                configurations.testConf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api") with project(":api")
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
        succeeds "impl:check"
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

                    dependencySubstitution {
                        substitute module("org.utils:api") with project(":api")
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
        succeeds "impl:check"
    }

    void "get useful error message when replacing an external dependency with a project that does not exist"()
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

                    dependencySubstitution {
                        substitute module("org.utils:api") with project(":doesnotexist")
                    }
                }

                task check << {
                    def deps = configurations.conf.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0] instanceof org.gradle.api.artifacts.result.UnresolvedDependencyResult
                }
            }
"""

        expect:
        fails "impl:check"
        errorOutput.contains(TextUtil.toPlatformLineSeparators("""
Execution failed for task ':impl:resolveConf'.
> Could not resolve all dependencies for configuration ':impl:conf'.
   > project ':doesnotexist' not found.
"""))
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

                configurations.compile.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api:1.5") with project(":api")
                }

                task check(dependsOn: configurations.compile) << {
                    def files = configurations.compile.files
                    assert files*.name.sort() == ["api.jar", "conf.txt"]
                }
            }
"""

        when:
        succeeds "impl:check"

        then:
        executedAndNotSkipped ":api:jar"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:impl") with project(":impl")
                }

                task check(dependsOn: configurations.conf) << {
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

        when:
        succeeds "test:check"

        then:
        notExecuted ":api:jar"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute project(":api") with module("org.utils:api:1.6")
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
        succeeds "impl:check"
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

                configurations.conf.resolutionStrategy.dependencySubstitution {
                    substitute module("org.utils:api:1.5") with project(":api")
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
        succeeds "impl:check"

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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') with module('org.utils:a:1.4')
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

        expect:
        succeeds "check"
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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') with module('org.utils:a:1.2.1')
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

        expect:
        succeeds "check"
    }

    def "can use custom versioning scheme"()
    {
        mavenRepo.module("org.utils", "api",  '1.3').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:default'
            }

            configurations.conf.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.version == 'default') {
                    it.useTarget group: it.requested.group, name: it.requested.module, version: '1.3'
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

        expect:
        succeeds "check"
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

            configurations.conf.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.version == 'default') {
                    it.useTarget group: it.requested.group, name: it.requested.module, version: '1.3'
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

        expect:
        succeeds "check"
    }

    void "rule selects unavailable version"()
    {
        mavenRepo.module("org.utils", "api", '1.3').publish()

        buildFile << """
            $common

            dependencies {
                conf 'org.utils:api:1.3'
            }

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:api:1.3') with module('org.utils:api:1.123.15')
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
        fails "check", "resolveConf"

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
                    all {
                        requested << "\$it.requested.module:\$it.requested.version"
                    }
                }
            }

            task check << {
                configurations.conf.resolve()
                assert requested == ['impl:1.3', 'foo:2.0', 'bar:2.0', 'api:1.3', 'api:1.5']
            }
"""

        expect:
        succeeds "check"
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
                    all {
                        it.useTarget group: it.requested.group, name: it.requested.module, version: '1.3' //happy
                    }
                    all {
                        throw new RuntimeException("Unhappy :(")
                    }
                }
            }
"""

        when:
        fails "resolveConf"

        then:
        failure.assertResolutionFailure(":conf")
                .assertHasCause("Could not resolve org.utils:impl:1.3.")
                .assertHasCause("Unhappy :(")
                .assertFailedDependencyRequiredBy(":root:1.0")
    }

    void "reasonable error message when attempting to substitute with an unversioned module selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute project(":foo") with module("org.gradle:test")
            }
"""

        when:
        fails "dependencies"

        then:
        failure.assertHasCause("Must specify version for target of dependency substitution")
    }

    void "reasonable error message when attempting to create an invalid selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module(":foo:bar:baz:") with module("")
            }
"""

        when:
        fails "dependencies"

        then:
        failure.assertHasCause("Cannot convert the provided notation to an object of type ComponentSelector: :foo:bar:baz:")
    }

    void "reasonable error message when attempting to add rule that substitutes with an unversioned module selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy.dependencySubstitution {
                def moduleSelector = module("org.gradle:test")
                all {
                    it.useTarget moduleSelector
                }
            }
"""

        when:
        fails "dependencies"

        then:
        failure.assertHasCause("Must specify version for target of dependency substitution")
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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') with module('org.utils:b:2.1')
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
        succeeds "check", "dependencies"

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

            configurations.conf.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.group == 'foo') {
                    it.useTarget('org:' + it.requested.module + ':' + it.requested.version)
                }
            }
"""

        when:
        succeeds "dependencies"

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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('foo:bar:baz') with module('org:b:1.0')
            }
"""

        when:
        succeeds "dependencies"

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

            configurations.conf.resolutionStrategy.dependencySubstitution.all {
                it.useTarget "foobar"
            }
"""

        when:
        fails "dependencies"

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

            configurations.conf.resolutionStrategy.dependencySubstitution {
                substitute module('org:a:1.0') with module('org:c:1.1')
            }
"""

        when:
        succeeds "dependencies"

        then:
        output.contains(toPlatformLineSeparators("""
conf
+--- org:a:1.0 -> org:c:2.0
\\--- org:a:2.0
     \\--- org:b:2.0
          \\--- org:c:2.0
"""))
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

            task jar(type: Jar) { baseName = project.name }
            artifacts { conf jar }

            task resolveConf(dependsOn: configurations.conf) << { configurations.conf.files }
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
