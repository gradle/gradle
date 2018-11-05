/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.tasks.diagnostics


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DependencyReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "omits repeated dependencies in case of circular dependencies"() {
        given:
        file("settings.gradle") << "include 'client', 'a', 'b', 'c'"

        buildFile << """
allprojects {
    configurations { compile; "default" { extendsFrom compile } }
    configurations { zzz }
    group = "group"
    version = 1.0
}

project(":a") {
    dependencies { compile project(":b") }
    dependencies { compile project(":c") }
}

project(":b") {
    dependencies { compile project(":c") }
}

project(":c") {
    dependencies { compile project(":a") }
}
"""

        when:
        run ":c:dependencies"

        then:
        output.contains """
compile
\\--- project :a
     +--- project :b
     |    \\--- project :c (*)
     \\--- project :c (*)
"""
        output.contains '(*) - dependencies omitted (listed previously)'
    }

    def "marks modules that can't be resolved as 'FAILED'"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").dependsOnModules("unknown").publish()
        mavenRepo.module("foo", "baz", "1.0").dependsOnModules("bar").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { foo }
            dependencies {
              foo 'i:dont:exist'
              foo 'foo:baz:1.0'
            }
        """

        when:
        executer.noExtraLogging()
        run "dependencies"

        then:
        output.contains """
foo
+--- i:dont:exist FAILED
\\--- foo:baz:1.0
     \\--- foo:bar:1.0
          \\--- foo:unknown:1.0 FAILED
"""
    }

    def "marks dynamic versions that can't be resolved as 'FAILED'"() {
        given:
        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { foo }
            dependencies {
                foo 'org:unknown:1.2+'
                foo 'org:unknown:[1.0,2.0]'
                foo 'org:unknown:latest.integration'
                foo 'org:unknown:latest.release'
                foo 'org:other:1.2'
                foo 'org:other:2.0+'
            }
        """

        when:
        executer.noExtraLogging()
        run "dependencies"

        then:
        output.contains """
foo
+--- org:unknown:1.2+ FAILED
+--- org:unknown:[1.0,2.0] FAILED
+--- org:unknown:latest.integration FAILED
+--- org:unknown:latest.release FAILED
+--- org:other:1.2 FAILED
\\--- org:other:2.0+ FAILED
"""
    }

    def "marks modules that can't be resolved after conflict resolution as 'FAILED'"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").dependsOn("foo", "baz", "2.0").publish()
        mavenRepo.module("foo", "baz", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { config }
            dependencies {
              config 'foo:bar:1.0'
              config 'foo:baz:1.0'
            }
        """

        when:
        executer.noExtraLogging()
        run "dependencies"

        then:
        output.contains """
config
+--- foo:bar:1.0
|    \\--- foo:baz:2.0 FAILED
\\--- foo:baz:1.0 -> 2.0 FAILED
"""
    }

    def "marks modules that can't be resolved after forcing a different version as 'FAILED'"() {
        given:
        mavenRepo.module("org", "libA", "1.0").dependsOn("org", "libB", "1.0").dependsOn("org", "libC", "1.0").publish()
        mavenRepo.module("org", "libB", "1.0").publish()
        mavenRepo.module("org", "libC", "1.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
              config {
                resolutionStrategy {
                  force('org:libB:2.0')
                  force('org:libC:1.2+')
                }
              }
            }
            dependencies {
              config 'org:libA:1.0'
            }
        """

        when:
        executer.noExtraLogging()
        run "dependencies"

        then:
        output.contains """
config
\\--- org:libA:1.0
     +--- org:libB:1.0 -> 2.0 FAILED
     \\--- org:libC:1.0 -> 1.2+ FAILED
"""
    }

    def "renders dependencies even if the configuration was already resolved"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { foo }
            dependencies {
                foo 'foo:bar:1.0'
                foo 'foo:bar:2.0'
            }

            task resolveConf {
                doLast {
                    configurations.foo.each { println it }
                }
            }
        """

        when:
        run "resolveConf", "dependencies"

        then:
        output.contains "foo:bar:1.0 -> 2.0"
    }

    def "renders selected versions in case of a conflict"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()
        mavenRepo.module("foo", "bar", "3.0").dependsOn('foo', 'baz', '5.0').publish()


        mavenRepo.module("foo", "baz", "5.0").publish()

        file("settings.gradle") << """include 'a', 'b', 'c', 'd', 'e'
rootProject.name = 'root'
"""

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(":a") {
                dependencies {
                    compile 'foo:bar:1.0'
                }
            }

            project(":b") {
                dependencies {
                    compile 'foo:bar:0.5.dont.exist'
                }
            }

            project(":c") {
                dependencies {
                    compile 'foo:bar:3.0'
                }
            }

            project(":d") {
                dependencies {
                    compile 'foo:bar:2.0'
                }
            }

            project(":e") {
                dependencies {
                    compile 'foo:bar:3.0'
                }
            }

            dependencies {
                compile project(":a"), project(":b"), project(":c"), project(":d"), project(":e")
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
compile - Dependencies for source set 'main' (deprecated, use 'implementation' instead).
+--- project :a
|    \\--- foo:bar:1.0 -> 3.0
|         \\--- foo:baz:5.0
+--- project :b
|    \\--- foo:bar:0.5.dont.exist -> 3.0 (*)
+--- project :c
|    \\--- foo:bar:3.0 (*)
+--- project :d
|    \\--- foo:bar:2.0 -> 3.0 (*)
\\--- project :e
     \\--- foo:bar:3.0 (*)
"""
    }

    def "renders the dependency tree"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOnModules('leaf3', 'leaf4').publish()

        mavenRepo.module("org", "toplevel").dependsOnModules("middle1", "middle2").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:toplevel:1.0'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
conf
\\--- org:toplevel:1.0
     +--- org:middle1:1.0
     |    +--- org:leaf1:1.0
     |    \\--- org:leaf2:1.0
     \\--- org:middle2:1.0
          +--- org:leaf3:1.0
          \\--- org:leaf4:1.0
"""
    }

    def "mentions web-based dependency report after legend"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()

        mavenRepo.module("org", "middle").dependsOnModules("leaf1", "leaf2").publish()

        mavenRepo.module("org", "top").dependsOnModules("middle", "leaf2").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:top:1.0'
            }
        """

        when:
        run "dependencies"

        then:
        output.contains """(*) - dependencies omitted (listed previously)

A web-based, searchable dependency report is available by adding the --scan option."""
    }

    def "shows selected versions in case of a multi-phase conflict"() {
        given:
        mavenRepo.module("foo", "foo", "1.0").publish()
        mavenRepo.module("foo", "foo", "2.0").publish()
        mavenRepo.module("foo", "foo", "3.0").publish()
        mavenRepo.module("foo", "foo", "4.0").publish()

        mavenRepo.module("bar", "bar", "5.0").dependsOn("foo", "foo", "4.0").publish()
        mavenRepo.module("bar", "bar", "6.0").dependsOn("foo", "foo", "3.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'bar:bar:5.0'
                conf 'bar:bar:6.0'
                conf 'foo:foo:1.0'
                conf 'foo:foo:2.0'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
conf
+--- bar:bar:5.0 -> 6.0
|    \\--- foo:foo:3.0
+--- bar:bar:6.0 (*)
+--- foo:foo:1.0 -> 3.0
\\--- foo:foo:2.0 -> 3.0
"""
    }

    def "deals with dynamic versions with conflicts"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()

        mavenRepo.module("foo", "foo", "1.0").dependsOn("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "foo", "2.0").dependsOn("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "foo", "2.5").dependsOn("foo", "bar", "2.0").publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'foo:foo:1+'
                conf 'foo:foo:2+'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
conf
+--- foo:foo:1+ -> 2.5
|    \\--- foo:bar:2.0
\\--- foo:foo:2+ -> 2.5 (*)
"""
    }

    def "renders ivy tree with custom configurations"() {
        given:
        def module = ivyRepo.module("org", "child")
        module.configuration('first', extendsFrom: ['second'])
        module.configuration('second')
        module.publish()

        module = ivyRepo.module("org", "parent").dependsOn('child')
        module.configuration('first', extendsFrom: ['second'])
        module.configuration('second')
        module.publish()

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:parent:1.0'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
conf
\\--- org:parent:1.0
     \\--- org:child:1.0
"""
    }

    def "renders the ivy tree with conflicts"() {
        given:
        ivyRepo.module("org", "leaf1").publish()
        ivyRepo.module("org", "leaf2").publish()
        ivyRepo.module("org", "leaf3").publish()
        ivyRepo.module("org", "leaf4").publish()
        ivyRepo.module("org", "leaf4", "2.0").publish()

        //also asserting on correct order of transitive dependencies
        ivyRepo.module("org", "middle1").dependsOn('leaf1', 'leaf2').publish()
        ivyRepo.module("org", "middle2").dependsOn('leaf3', 'leaf4') publish()

        ivyRepo.module("org", "toplevel").dependsOn("middle1", "middle2").publish()

        file("build.gradle") << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }

            configurations {
                conf
            }
            dependencies {
                conf 'org:toplevel:1.0', 'org:leaf4:2.0'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
conf
+--- org:toplevel:1.0
|    +--- org:middle1:1.0
|    |    +--- org:leaf1:1.0
|    |    \\--- org:leaf2:1.0
|    \\--- org:middle2:1.0
|         +--- org:leaf3:1.0
|         \\--- org:leaf4:1.0 -> 2.0
\\--- org:leaf4:2.0
"""
    }

    def "tells if there are no dependencies"() {
        given:
        buildFile << "configurations { foo }"

        when:
        run "dependencies"

        then:
        output.contains """
foo
No dependencies
"""
    }

    def "tells if there are no configurations"() {
        when:
        run "dependencies"

        then:
        output.contains "No configurations"
    }

    def "dependencies report does not run for subprojects by default"() {
        given:
        file("settings.gradle") << "include 'a'"

        file("build.gradle") << """
        project(":a") {
          configurations { foo }
          dependencies {
            foo "i.dont.exist:foo:1.0"
          }
        }
"""
        when:
        run "dependencies"

        then:
        noExceptionThrown()
        //note that 'a' project dependencies are not being resolved
    }

    def "report can be limited to a single configuration via command-line parameter"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "toplevel1").dependsOnModules('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "toplevel2").dependsOnModules('leaf3', 'leaf4').publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf1
                conf2
            }

            dependencies {
                conf1 'org:toplevel1:1.0'
                conf2 'org:toplevel2:1.0'
            }
        """

        when:
        run "dependencies", "--configuration", "conf2"

        then:
        output.contains """
conf2
\\--- org:toplevel2:1.0
     +--- org:leaf3:1.0
     \\--- org:leaf4:1.0
"""

        !output.contains("conf1")
    }

    void "marks module that cannot be resolved due to broken dependency rule as 'FAILED'"() {
        mavenRepo.module("org.utils", "impl", '1.3').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }

            configurations { conf }

            dependencies {
                conf 'org.utils:impl:1.3'
            }

            configurations.conf.resolutionStrategy {
                eachDependency {
                    throw new RuntimeException("Ka-booom!")
	            }
	        }
"""

        when:
        run "dependencies"

        then:
        output.contains """
conf
\\--- org.utils:impl:1.3 FAILED
"""
    }

    def "renders a mix of project and external dependencies"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()

        file("settings.gradle") << """include 'a', 'b', 'a:c', 'd', 'e'
rootProject.name = 'root'
"""

        file("build.gradle") << """
            allprojects {
                apply plugin: 'java'
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(":a") {
               dependencies {
                    compile 'foo:bar:1.0'
                }
            }

            project(":b") {
               dependencies {
                    compile 'foo:bar:0.5.dont.exist'
                }
            }

            project(":a:c") {
               dependencies {
                    compile 'foo:bar:2.0'
               }
            }

            project(":d") {
               dependencies {
                    compile project(":e")
                }
            }

            project(":e") {
               dependencies {
                    compile 'foo:bar:2.0'
                }
            }

            dependencies {
                compile project(":a"), project(":b"), project(":a:c"), project(":d")
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
compile - Dependencies for source set 'main' (deprecated, use 'implementation' instead).
+--- project :a
|    \\--- foo:bar:1.0 -> 2.0
+--- project :b
|    \\--- foo:bar:0.5.dont.exist -> 2.0
+--- project :a:c
|    \\--- foo:bar:2.0
\\--- project :d
     \\--- project :e
          \\--- foo:bar:2.0
"""
    }

    def "reports external dependency replaced with project dependency"() {
        mavenRepo.module("org.utils", "api",  '1.3').publish()

        file("settings.gradle") << "include 'client', 'api2', 'impl'"

        buildFile << """
            allprojects {
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                configurations {
                    compile
                }

                group "org.somethingelse"
            }

            project(":api2") {
                version = '1.5'
            }

            project(":impl") {
                dependencies {
                    compile group: 'org.utils', name: 'api', version: '1.3', configuration: 'compile'
                }

                configurations.compile.resolutionStrategy.dependencySubstitution {
                    substitute module('org.utils:api:1.3') with project(':api2')
                }
            }
"""

        when:
        run(":impl:dependencies")

        then:
        output.contains """
compile
\\--- org.utils:api:1.3 -> project :api2
"""
    }

    def "reports external dependency with version updated by resolve rule"() {
        mavenRepo.module("org.utils", "api", '0.1').publish()

        file("settings.gradle") << "include 'client', 'impl'"

        buildFile << """
            allprojects {
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                configurations {
                    compile
                }

                group "org.utils"
            }

            project(":impl") {
                dependencies {
                    compile group: 'org.utils', name: 'api', version: '1.3'
                }

                configurations.compile.resolutionStrategy.eachDependency {
                    if (it.requested.version == '1.3') {
                        it.useVersion '0.1'
                    }
                }
            }
"""

        when:
        run(":impl:dependencies")

        then:
        output.contains """
compile
\\--- org.utils:api:1.3 -> 0.1
"""
    }

    def "reports external dependency substituted with another"() {
        mavenRepo.module("org.utils", "api", '0.1').publish()
        mavenRepo.module("org.other", "another", '0.1').publish()

        file("settings.gradle") << "include 'client', 'impl'"

        buildFile << """
            allprojects {
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                configurations {
                    compile
                }

                group "org.utils"
            }

            project(":impl") {
                dependencies {
                    compile group: 'org.utils', name: 'api', version: '1.3'
                    compile group: 'org.original', name: 'original', version: '1.0'
                }

                configurations.compile.resolutionStrategy.dependencySubstitution {
                    substitute module('org.original:original') with module('org.other:another:0.1')
                    substitute module('org.utils:api') with module('org.utils:api:0.1')
                }
            }
"""

        when:
        run(":impl:dependencies")

        then:
        output.contains """
compile
+--- org.utils:api:1.3 -> 0.1
\\--- org.original:original:1.0 -> org.other:another:0.1
"""
    }

    void "doesn't fail if a configuration is not resolvable"() {
        mavenRepo.module("foo", "foo", '1.0').publish()
        mavenRepo.module("foo", "bar", '2.0').publish()

        file("build.gradle") << """
            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            configurations {
                api.canBeResolved = false
                compile.extendsFrom api
            }
            dependencies {
                api 'foo:foo:1.0'
                compile 'foo:bar:2.0'
            }
        """

        when:
        run ":dependencies"

        then:
        output.contains """
api (n)
\\--- foo:foo:1.0 (n)

compile
+--- foo:foo:1.0
\\--- foo:bar:2.0

(n) - Not resolved (configuration is not meant to be resolved)
"""

        when:
        run ":dependencies", "--configuration", "api"

        then:
        output.contains """
api (n)
\\--- foo:foo:1.0 (n)

(n) - Not resolved (configuration is not meant to be resolved)
"""
    }

    def "renders dependency constraints non-transitively"() {
        def moduleC = mavenRepo.module('group', 'moduleC', '1.0').publish()
        def moduleB = mavenRepo.module('group', 'moduleB', '1.0').dependsOn(moduleC).publish()
        def moduleA = mavenRepo.module('group', 'moduleA', '2.0').dependsOn(moduleB).publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { conf }
            dependencies {
                constraints {
                    conf 'group:moduleA:2.0'
                    conf 'group:moduleC:1.0'
                }
            }
            dependencies {
                conf 'group:moduleA'
            }
"""
        when:
        run ":dependencies", "--configuration", "conf"

        then:
        output.contains """
conf
+--- group:moduleA -> 2.0
|    \\--- group:moduleB:1.0
|         \\--- group:moduleC:1.0
+--- group:moduleA:2.0 (c)
\\--- group:moduleC:1.0 (c)
"""
    }

    def "renders version constraints"() {
        mavenRepo.module('group', 'moduleA', '1.0').publish()
        mavenRepo.module('group', 'moduleB', '1.0').publish()
        mavenRepo.module('group', 'moduleC', '1.0').publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { conf }
            dependencies {
                constraints {
                }
            }
            dependencies {
                conf('group:moduleA') {
                    version {
                        require '1.+'
                        prefer '1.0'
                    }
                }
                conf('group:moduleB') {
                    version {
                        strictly '1.0'
                    }
                }
                conf('group:moduleC') {
                    version {
                        require '1.0'
                        reject '1.1', '1.2'
                    }
                }
            }
"""
        when:
        run ":dependencies", "--configuration", "conf"

        then:
        output.contains """
conf
+--- group:moduleA:{require 1.+; prefer 1.0} -> 1.0
+--- group:moduleB:{strictly 1.0}
\\--- group:moduleC:{require 1.0; reject 1.1 & 1.2}
"""
    }

    def "reports imported BOM as a set of dependency constraints"() {
        def moduleC = mavenRepo.module('group', 'moduleC', '1.0').publish()
        def moduleB = mavenRepo.module('group', 'moduleB', '1.0').dependsOn(moduleC).publish()
        def moduleA = mavenRepo.module('group', 'moduleA', '2.0').dependsOn(moduleB).publish()
        mavenRepo.module('group', 'bom', '1.0')
                .hasType("pom")
                .dependencyConstraint(moduleA)
                .dependencyConstraint(moduleC)
                .publish()

        buildFile << """
            apply plugin: 'java' // Java plugin required for BOM import
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                implementation platform('group:bom:1.0')
                implementation 'group:moduleA'
            }
"""
        when:
        run ":dependencies", "--configuration", "compileClasspath"

        then:
        output.contains """
compileClasspath - Compile classpath for source set 'main'.
+--- group:bom:1.0
|    +--- group:moduleA:2.0 (c)
|    \\--- group:moduleC:1.0 (c)
\\--- group:moduleA -> 2.0
     \\--- group:moduleB:1.0
          \\--- group:moduleC:1.0

(c) - dependency constraint
"""
    }
}
