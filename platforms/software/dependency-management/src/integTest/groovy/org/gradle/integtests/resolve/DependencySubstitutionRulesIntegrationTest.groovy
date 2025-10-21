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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

import java.util.concurrent.CopyOnWriteArrayList

class DependencySubstitutionRulesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")

    def setup() {
        settingsFile << """
            rootProject.name = "depsub"
            ${resolve.configureSettings()}
        """
    }

    void "forces multiple modules by rule"() {
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
                implementation("org.stuff:foo:2.0")
                implementation("org.utils:impl:1.3")
                implementation("org.utils:optional-lib:5.0")
            }

            configurations.runtimeClasspath.resolutionStrategy {
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

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org.stuff:foo:2.0") {
                    edge("org.utils:api:1.5", "org.utils:api:1.5") {
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

    void "module forced by rule has correct selection reason"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        mavenRepo.module("org.stuff", "foo", '2.0').dependsOn('org.utils', 'impl', '1.3') publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.stuff:foo:2.0")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                dependencySubstitution {
                    all {
                        if (it.requested.group == 'org.utils') {
                            it.useTarget group: 'org.utils', name: it.requested.module, version: '1.5'
                        }
                    }
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org.stuff:foo:2.0") {
                    edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                        selectedByRule()
                        module("org.utils:api:1.5").selectedByRule()
                    }
                }
            }
        }
    }

    void "all rules are executed in order and last one wins"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                dependencySubstitution {
                    all {
                        it.useTarget group: it.requested.group, name: it.requested.module, version: '1.4'
                    }
                    all {
                        it.useTarget group: it.requested.group, name: it.requested.module, version: '1.5'
                    }
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    selectedByRule()
                    module("org.utils:api:1.5").selectedByRule()
                }
            }
        }
    }

    void "all rules are executed in order and last one wins, including resolution rules"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                dependencySubstitution {
                    all {
                        it.useTarget group: 'org.utils', name: it.requested.module, version: '1.4'
                    }
                }
                eachDependency {
                    it.useVersion '1.5'
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    selectedByRule()
                    module("org.utils:api:1.5").selectedByRule()
                }
            }
        }
    }

    void "can unforce the version"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                force("org.utils:impl:1.5", "org.utils:api:1.5")

                dependencySubstitution {
                    all {
                        it.useTarget it.requested
                    }
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.3") {
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

    void "forced modules and rules coexist"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()

        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.6').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                force("org.utils:impl:1.5")

                dependencySubstitution {
                    substitute module("org.utils:api") using module("org.utils:api:1.6")
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:impl:1.3", "org.utils:impl:1.5") {
                    forced()
                    edge("org.utils:api:1.5", "org.utils:api:1.6").selectedByRule()
                }
            }
        }
    }

    void "rule selects a dynamic version"() {
        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.4').publish()
        mavenRepo.module("org.utils", "api", '1.5').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:api:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:api:1.3') using module('org.utils:api:1.+')
            }

            task check {
                doLast {
                    def deps = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies as List
                    assert deps.size() == 1
                    assert deps[0].requested.version == '1.3'
                    assert deps[0].selected.id.version == '1.5'
                    assert !deps[0].selected.selectionReason.forced
                    assert deps[0].selected.selectionReason.selectedByRule
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:api:1.3", "org.utils:api:1.5").selectedByRule()
            }
        }
    }

    void "can substitute modules with project dependency using #name"() {
        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common
        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:api:1.5") {
                    targetConfiguration = "runtimeElements"
                }
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("$selector") using project(":api")
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("org.utils:api:1.5", ":api", "depsub:api:") {
                    configuration = "runtimeElements"
                    selectedByRule()
                }
            }
        }

        and:
        executedAndNotSkipped ":api:jar"

        where:
        name                 | selector
        "matching module"    | "org.utils:api"
        "matching component" | "org.utils:api:1.5"
    }

    void "can access built artifacts from substituted project dependency"() {
        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << """
            $common

            task build {
                def outFile = file("artifact.txt")
                outputs.file(outFile)
                doLast {
                    outFile << "Lajos"
                }
            }

            artifacts {
                runtimeElements (file("artifact.txt")) {
                    builtBy build
                }
            }
        """

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:api:1.5")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:api") using project(":api")
            }

            tasks.register("check") {
                def files = configurations.runtimeClasspath
                dependsOn(files)
                doLast {
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

    void "can replace project dependency #projectGroup:api:#projectVersion with external dependency org.utils:api:1.5"() {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << """
            $common
            group = "$projectGroup"
            version = "$projectVersion"
        """

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation(project(path: ":api"))
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute project(":api") using module("org.utils:api:1.5")
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        notExecuted ":api:jar"

        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("project :api", "org.utils:api:1.5") {
                    selectedByRule()
                }
            }
        }

        where:
        projectVersion | projectGroup   | scenario
        "1.5"          | "org.utils"    | "the same as the external dependency"
        "2.0"          | "org.utils"    | "GAV different, version only"
        "1.5"          | "my.org.utils" | "GAV different, group only"
        "2.0"          | "my.org.utils" | "GAV different, version and group"
    }

    void "can replace transitive external dependency with project dependency"() {
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()
        settingsFile << 'include "api", "test"'

        buildFile << common

        file("api/build.gradle") << common

        file("test/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:impl:1.5")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:api") using project(":api")
            }

            tasks.register("buildConf") {
                dependsOn(configurations.runtimeClasspath)
            }
        """

        when:
        run ":test:checkDeps"

        then:
        resolve.expectGraph {
            root(":test", "depsub:test:") {
                module("org.utils:impl:1.5") {
                    edge("org.utils:api:1.5", ":api", "depsub:api:") {
                        configuration = "runtimeElements"
                        selectedByRule()
                    }
                }
            }
        }

        and:
        executedAndNotSkipped ":api:jar"
    }

    void "can replace external dependency declared in extended configuration with project dependency"() {
        mavenRepo.module("org.utils", "api", '1.5').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common

        file("impl/build.gradle") << """
            $common

            configurations {
                subConf
                implementation.extendsFrom subConf
            }

            dependencies {
                subConf("org.utils:api:1.5")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:api") using project(":api")
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("org.utils:api:1.5", ":api", "depsub:api:") {
                    selectedByRule()
                }
            }
        }
    }

    void "can replace forced external dependency with project dependency"() {
        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:api:1.5")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                force("org.utils:api:1.3")

                dependencySubstitution {
                    substitute module("org.utils:api") using project(":api")
                }
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("org.utils:api:1.5", ":api", "depsub:api:") {
                    forced()
                    selectedByRule()
                }
            }
        }
    }

    void "get useful error message when replacing an external dependency with a project that does not exist"() {
        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:api:1.5")
            }

            configurations.runtimeClasspath.resolutionStrategy {
                force("org.utils:api:1.3")

                dependencySubstitution {
                    substitute module("org.utils:api") using project(":doesnotexist")
                }
            }
        """

        when:
        fails ":impl:checkDeps"

        then:
        failure.assertHasDescription("A problem occurred evaluating project ':impl'.")
        failure.assertHasCause("Project with path ':doesnotexist' not found in build ':'.")
    }

    void "replacing external module dependency with project dependency keeps the original configuration"() {
        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:api:1.5") {
                    targetConfiguration = "runtimeElements"
                }
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:api:1.5") using project(":api")
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("org.utils:api:1.5", ":api", "depsub:api:") {
                    configuration = 'runtimeElements'
                    selectedByRule()
                }
            }
        }
    }

    void "replacing external module dependency with project dependency keeps the original transitivity"() {
        mavenRepo.module("org.utils", "impl", '1.5').dependsOn('org.utils', 'api', '1.5').publish()
        settingsFile << 'include "impl", "test"'

        buildFile << common

        file("impl/build.gradle") << common

        file("test/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:impl:1.5") {
                    transitive = false
                }
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:impl") using project(":impl")
            }
        """

        when:
        run ":test:checkDeps"

        then:
        resolve.expectGraph {
            root(":test", "depsub:test:") {
                edge("org.utils:impl:1.5", ":impl", "depsub:impl:") {
                    selectedByRule()
                }
            }
        }
    }

    void "external dependency substituted for a project dependency participates in conflict resolution"() {
        mavenRepo.module("org.utils", "api", '2.0').publish()

        settingsFile << 'include "api", "impl"'

        buildFile << common

        file("api/build.gradle") << common

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation(project(":api"))
                implementation("org.utils:api:2.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute project(":api") using module("org.utils:api:1.6")
            }

            task check {
                doLast {
                    def deps = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies as List
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

                    def resolvedDeps = configurations.runtimeClasspath.resolvedConfiguration.firstLevelModuleDependencies
                    resolvedDeps.size() == 1
                    resolvedDeps[0].module.id == moduleId("org.utils", "api", "2.0")
                }
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                module("org.utils:api:2.0")
                edge("project :api", "org.utils:api:2.0").byConflictResolution("between versions 2.0 and 1.6").selectedByRule()
            }
        }
    }

    void "project dependency substituted for an external dependency participates in conflict resolution"() {
        mavenRepo.module("org.utils", "dep1", '2.0').publish()
        mavenRepo.module("org.utils", "dep2", '2.0').publish()
        settingsFile << 'include "impl", "dep1", "dep2"'

        buildFile << common

        file("dep1/build.gradle") << """
            $common

            group = "org.utils"
            version = '1.6'
        """

        file("dep2/build.gradle") << """
            $common

            group = "org.utils"
            version = '3.0'

            jar.archiveVersion = '3.0'
        """

        file("impl/build.gradle") << """
            $common

            dependencies {
                implementation("org.utils:dep1:1.5")
                implementation("org.utils:dep1:2.0")

                implementation("org.utils:dep2:1.5")
                implementation("org.utils:dep2:2.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module("org.utils:dep1:1.5") using project(":dep1")
                substitute module("org.utils:dep2:1.5") using project(":dep2")
            }
        """

        when:
        run ":impl:checkDeps"

        then:
        resolve.expectGraph {
            root(":impl", "depsub:impl:") {
                edge("org.utils:dep1:1.5", "org.utils:dep1:2.0") {
                    byConflictResolution("between versions 1.6 and 2.0")
                    selectedByRule()
                }
                edge("org.utils:dep1:2.0", "org.utils:dep1:2.0")

                edge("org.utils:dep2:1.5", ":dep2", "org.utils:dep2:3.0") {
                    selectedByRule()
                    byConflictResolution("between versions 3.0 and 2.0")
                }
                edge("org.utils:dep2:2.0", "org.utils:dep2:3.0")

            }
        }
    }

    void "can deny a version"() {
        mavenRepo.module("org.utils", "a", '1.4').publish()
        mavenRepo.module("org.utils", "a", '1.3').publish()
        mavenRepo.module("org.utils", "a", '1.2').publish()
        mavenRepo.module("org.utils", "b", '1.3').dependsOn("org.utils", "a", "1.3").publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:a:1.2")
                implementation("org.utils:b:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') using module('org.utils:a:1.4')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org.utils:b:1.3") {
                    edge("org.utils:a:1.3", "org.utils:a:1.4").selectedByRule().byConflictResolution("between versions 1.4 and 1.3")
                }
                edge("org.utils:a:1.2", "org.utils:a:1.4")
            }
        }
    }

    void "can deny a version that is not used"() {
        mavenRepo.module("org.utils", "a", '1.3').publish()
        mavenRepo.module("org.utils", "a", '1.2').publish()
        mavenRepo.module("org.utils", "b", '1.3').dependsOn("org.utils", "a", "1.3").publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:a:1.2")
                implementation("org.utils:b:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') using module('org.utils:a:1.2.1')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org.utils:b:1.3") {
                    module("org.utils:a:1.3")
                }
                edge("org.utils:a:1.2", "org.utils:a:1.3") {
                    selectedByRule()
                    byConflictResolution("between versions 1.3 and 1.2.1")
                }
            }
        }
    }

    def "can use custom versioning scheme"() {
        mavenRepo.module("org.utils", "api", '1.3').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:api:default")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.version == 'default') {
                    it.useTarget group: it.requested.group, name: it.requested.module, version: '1.3'
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:api:default", "org.utils:api:1.3").selectedByRule()
            }
        }
    }

    def "can use custom versioning scheme for transitive dependencies"() {
        mavenRepo.module("org.utils", "api", '1.3').publish()
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', 'default').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.version == 'default') {
                    it.useTarget group: it.requested.group, name: it.requested.module, version: '1.3'
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org.utils:impl:1.3") {
                    edge("org.utils:api:default", "org.utils:api:1.3").selectedByRule()
                }
            }
        }
    }

    void "rule selects unavailable version"() {
        mavenRepo.module("org.utils", "api", '1.3').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:api:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:api:1.3') using module('org.utils:api:1.123.15')
            }

            task check {
                def root = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    def deps = root.get().dependencies as List
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
        succeeds "check"
        fails "checkDeps"

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':runtimeClasspath'.")
        failure.assertHasCause("Could not find org.utils:api:1.123.15")
    }

    void "rules triggered exactly once per the same dependency"() {
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
                implementation("org.utils:impl:1.3")
                implementation("org.stuff:foo:2.0")
                implementation("org.stuff:bar:2.0")
            }

            List requested = new ${CopyOnWriteArrayList.name}()

            configurations.runtimeClasspath.resolutionStrategy {
                dependencySubstitution {
                    all {
                        requested << "\$it.requested.module:\$it.requested.version"
                    }
                }
            }

            task check {
                def files = configurations.runtimeClasspath
                dependsOn(files)
                doLast {
                    files.forEach { }
                    requested = requested.sort()
                    assert requested == [ 'api:1.3', 'api:1.5', 'bar:2.0', 'foo:2.0', 'impl:1.3']
                }
            }
        """

        expect:
        succeeds "check"
    }

    void "runtime exception when evaluating rule yields decent exception"() {
        mavenRepo.module("org.utils", "impl", '1.3').dependsOn('org.utils', 'api', '1.3').publish()
        mavenRepo.module("org.utils", "api", '1.3').publish()

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy {
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
        fails "checkDeps"

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':runtimeClasspath'.")
        failure.assertHasCause("""Could not resolve org.utils:impl:1.3.
Required by:
    root project 'root'""")
        failure.assertHasCause("Unhappy :(")
    }

    void "reasonable error message when attempting to substitute with an unversioned module selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute project(":") using module("org.gradle:test")
            }
        """

        when:
        fails "checkDeps"

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Must specify version for target of dependency substitution")
    }

    void "reasonable error message when attempting to create an invalid selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module(":foo:bar:baz:") using module("")
            }
        """

        when:
        fails "checkDeps"

        then:
        failure.assertHasCause("Cannot convert the provided notation to an object of type ComponentSelector: :foo:bar:baz:")
    }

    void "reasonable error message when attempting to add rule that substitutes with an unversioned module selector"() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            version = 1.0

            $common

            dependencies {
                implementation("org.utils:impl:1.3")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                def moduleSelector = module("org.gradle:test")
                all {
                    it.useTarget moduleSelector
                }
            }
        """

        when:
        fails "checkDeps"

        then:
        failure.assertHasCause("Must specify version for target of dependency substitution")
    }

    void "can substitute module name and resolve conflict"() {
        mavenRepo.module("org.utils", "a", '1.2').publish()
        mavenRepo.module("org.utils", "b", '2.0').publish()
        mavenRepo.module("org.utils", "b", '2.1').publish()

        buildFile << """
            $common

            dependencies {
                implementation("org.utils:a:1.2")
                implementation("org.utils:b:2.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org.utils:a:1.2') using module('org.utils:b:2.1')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org.utils:a:1.2", "org.utils:b:2.1").selectedByRule().byConflictResolution("between versions 2.1 and 2.0")
                edge("org.utils:b:2.0", "org.utils:b:2.1")
            }
        }
    }

    def "can substitute module group"() {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "c").publish()
        //a1
        //b->a2->c

        buildFile << """
            $common

            dependencies {
                implementation("org:a:1.0")
                implementation("foo:b:1.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution.all {
                if (it.requested.group == 'foo') {
                    it.useTarget('org:' + it.requested.module + ':' + it.requested.version)
                }
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
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

    def "can substitute module group, name and version"() {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "c").publish()
        //a1
        //b->a2->c

        buildFile << """
            $common

            dependencies {
                implementation("org:a:1.0")
                implementation("foo:bar:baz")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('foo:bar:baz') using module('org:b:1.0')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
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

    def "provides decent feedback when target module incorrectly specified"() {
        buildFile << """
            $common

            dependencies {
                implementation("org:a:1.0")
                implementation("foo:bar:baz")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution.all {
                it.useTarget "foobar"
            }
        """

        when:
        fails "checkDeps"

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':runtimeClasspath'.")
        failure.assertHasCause("Invalid format: 'foobar'")
    }

    def "substituted module version participates in conflict resolution"() {
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "b", "2.0").publish()
        mavenRepo.module("org", "b", "2.0").dependsOn("org", "c", "2.0").publish()
        mavenRepo.module("org", "c", "2.0").publish()

        buildFile << """
            $common

            dependencies {
                implementation("org:a:1.0")
                implementation("org:a:2.0")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('org:a:1.0') using module('org:c:1.1')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
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

    String getCommon() {
        """
        configurations {
            def implementation = dependencyScope("implementation")
            resolvable("runtimeClasspath") {
                extendsFrom(implementation.get())
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "FOOBAR"))
                }
            }
            consumable("runtimeElements") {
                extendsFrom(implementation.get())
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "FOOBAR"))
                }
            }
        }

        ${mavenTestRepository()}

        task jar(type: Jar) {
            archiveBaseName = project.name
            // TODO LJA: No idea why I have to do this
            if (project.version != 'unspecified') {
                archiveFileName = "\${project.name}-\${project.version}.jar"
            }
            destinationDirectory = buildDir
        }

        configurations.runtimeElements.outgoing {
            artifact jar
        }

        def moduleId(String group, String name, String version) {
            return org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(group, name, version)
        }

        def projectId(String projectPath) {
            return org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newId(projectPath)
        }
        """
    }

    void "custom dependency substitution reasons are available in resolution result"() {
        mavenRepo.module("org", "a", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "c").publish()
        //a1
        //b->a2->c

        buildFile << """
            $common

            dependencies {
                implementation("org:a:1.0")
                implementation("foo:bar:baz")
            }

            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                substitute module('foo:bar:baz') because('we need integration tests') using module('org:b:1.0')
            }
        """

        when:
        run "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:c:1.0")
                }
                edge("foo:bar:baz", "org:b:1.0") {
                    selectedByRule('we need integration tests')
                    module("org:a:2.0")
                }
            }
        }
    }

    @Issue("gradle/gradle#5692")
    def 'substitution with project does not trigger failOnVersionConflict'() {
        settingsFile << 'include "sub"'
        buildFile << """
            $common

            dependencies {
                implementation("foo:bar:1")
                implementation(project(":sub"))
            }

            configurations.all {
                resolutionStrategy {
                    dependencySubstitution { DependencySubstitutions subs ->
                        subs.substitute(subs.module('foo:bar:1')).using(subs.project(":sub"))
                    }
                    failOnVersionConflict()
                }
            }
        """

        file("sub/build.gradle") << """
            version = '0.0.1'
            group = 'org.test'

            $common
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                project(':sub', 'org.test:sub:0.0.1') {
                    configuration = 'default'
                    selectedByRule()
                }
                edge('foo:bar:1', 'org.test:sub:0.0.1')
            }
        }
    }

    def "should fail not crash if empty selector skipped"() {
        given:
        buildFile << """
            $common
            configurations {
                runtimeClasspath {
                    resolutionStrategy.dependencySubstitution {
                        all { DependencySubstitution dependency ->
                            throw new RuntimeException('Substitution exception')
                        }
                    }
                }
            }
            dependencies {
                implementation("org:foo:1.0")
                constraints {
                    implementation("org:foo")
                }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Substitution exception")
    }

    def "can substitute a classified dependency with a non classified version"() {
        def v1 = mavenRepo.module("org", "lib", "1.0")
            .artifact(classifier: 'classy')
            .publish()
        // classifier doesn't exist anymore
        def v2 = mavenRepo.module("org", "lib", "1.1").publish()
        def trigger = mavenRepo.module("org", "other", "1.0")
            .dependsOn(v2)
            .publish()

        buildFile << """
            $common

            configurations {
                runtimeClasspath {
                    resolutionStrategy.$notation
                }
            }

            dependencies {
                implementation("org:lib:1.0:classy")
                implementation("org:other:1.0")
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', 'org:lib:1.1') {
                    selectedByRule()
                    byConflictResolution("between versions 1.1 and 1.0")
                }
                module('org:other:1.0') {
                    module('org:lib:1.1')
                }
            }
        }

        where:
        notation << [
            """dependencySubstitution {
                  substitute module('org:lib:1.0') using module('org:lib:1.0') withoutClassifier()
               }""",
            """dependencySubstitution.all { DependencySubstitution dependency ->
                  if (dependency.requested instanceof ModuleComponentSelector && dependency.requested.module == 'lib') {
                     dependency.artifactSelection {
                        selectArtifact('jar', 'jar', null)
                     }
                  }
               }""",
            """eachDependency { dep ->
                  if (dep.requested.name == 'lib') {
                     dep.artifactSelection {
                        selectArtifact('jar', 'jar', null)
                     }
                  }
               }
            """,
            """
               dependencySubstitution {
                  substitute module('org:lib:1.0') using module('org:lib:1.0') withoutArtifactSelectors()
               }
            """
        ]
    }

    def "can substitute a non classified dependency with a classified version"() {
        def v1 = mavenRepo.module("org", "lib", "1.0")
            .publish()
        // classifier doesn't exist anymore
        def v2 = mavenRepo.module("org", "lib", "1.1")
            .artifact(classifier: 'classy')
            .publish()
        def trigger = mavenRepo.module("org", "other", "1.0")
            .dependsOn(v2)
            .publish()

        buildFile << """
            $common

            configurations {
                runtimeClasspath {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:lib') using module('org:lib:1.1') withClassifier('classy')
                    }
                }
            }

            dependencies {
                implementation("org:lib:1.0")
                implementation("org:other:1.0")
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', 'org:lib:1.1') {
                    artifact(classifier: 'classy')
                    selectedByRule()
                }
                module('org:other:1.0') {
                    module('org:lib:1.1')
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/13658")
    def "constraint shouldn't be converted to hard dependency when a dependency substitution applies on an external module"() {
        def fooModule = mavenRepo.module("org", "foo", "1.0")
        mavenRepo.module("org", "platform", "1.0")
            .asGradlePlatform()
            .dependencyConstraint(fooModule)
            .publish()

        settingsFile << """
            include 'lib'
        """

        file('lib/build.gradle') << """
            plugins {
                id("java-library")
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                api platform('org:platform:1.0')
            }

            configurations.all {
                resolutionStrategy.dependencySubstitution {
                    substitute module('org:foo:1.0') using project(":lib")
                }
            }
        """

        when:
        run(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("org:platform:1.0") {
                    noArtifacts()
                }
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/33490")
    def "can substitute module with project and use withoutArtifactSelectors"() {
        mavenRepo.module("com.external", "libB", "1.0")
            .dependsOn("com.external", "libC", "1.0", "type")
            .publish()

        settingsFile << """
            include("libC")
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("com.external:libB:1.0")
            }

            def withoutArtifacts = providers.systemProperty("withoutArtifacts")
            configurations.runtimeClasspath.resolutionStrategy.dependencySubstitution {
                def sub = substitute(module("com.external:libC:1.0"))
                    .using(project(":libC"))

                if (withoutArtifacts.isPresent()) {
                    sub.withoutArtifactSelectors()
                }
            }
        """

        file("libC/build.gradle") << """
            plugins {
                id("java-library")
            }
        """

        when:
        fails(":checkDeps")

        then:
        failure.assertHasCause("Could not find libC.type (project :libC)")

        when:
        succeeds(":checkDeps", "-DwithoutArtifacts=true")

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                module("com.external:libB:1.0") {
                    edge("com.external:libC:1.0", ":libC", "depsub:libC:") {
                        selectedByRule()
                    }
                }
            }
        }

    }
}
