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
import spock.lang.Ignore

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DependencyReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        distribution.requireOwnUserHomeDir()
    }

    def "omits repeated dependencies in case of circular dependencies"() {
        given:
        file("settings.gradle") << "include 'client', 'a', 'b', 'c'"

        [a: "b", b: "c", c: "a"].each { module, dep ->
            def upped = module.toUpperCase()
            file(module, "build.gradle") << """
                apply plugin: 'java'
                group = "group"
                version = 1.0

                dependencies {
                    compile project(":$dep")
                }
            """
            file(module, "src", "main", "java", "${upped}.java") << "public class $upped {}"
        }

        and:
        file("client", "build.gradle") << """
            apply plugin: 'java'
            
            dependencies {
                compile project(":a")
            }
        """
        
        when:
        run ":client:dependencies"
        
        then:
        output.contains "(*) - dependencies omitted (listed previously)"
    }

    def "marks modules that can't be resolved as 'FAILED'"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).dependsOn("unknown").publish()
        mavenRepo.module("foo", "baz", 1.0).dependsOn("bar").publish()

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
        executer.allowExtraLogging = false
        run "dependencies"

        then:
        output.contains(toPlatformLineSeparators("""
foo
+--- i:dont:exist FAILED
\\--- foo:baz:1.0
     \\--- foo:bar:1.0
          \\--- foo:unknown:1.0 FAILED
"""
        ))
    }

    def "marks modules that can't be resolved after conflict resolution as 'FAILED'"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).dependsOn("foo", "baz", "2.0").publish()
        mavenRepo.module("foo", "baz", 1.0).publish()

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
        executer.allowExtraLogging = false
        run "dependencies"

        then:
        output.contains(toPlatformLineSeparators("""
config
+--- foo:bar:1.0
|    \\--- foo:baz:2.0 FAILED
\\--- foo:baz:1.0 -> 2.0 FAILED
"""
        ))
    }

    def "marks modules that can't be resolved after forcing a different version as 'FAILED'"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).dependsOn("foo", "baz", "1.0").publish()
        mavenRepo.module("foo", "baz", 1.0).publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
              config {
                resolutionStrategy {
                  force('foo:baz:2.0')
                }
              }
            }
            dependencies {
              config 'foo:bar:1.0'
            }
        """

        when:
        executer.allowExtraLogging = false
        run "dependencies"

        then:
        output.contains(toPlatformLineSeparators("""
config
\\--- foo:bar:1.0
     \\--- foo:baz:1.0 -> 2.0 FAILED
"""
        ))
    }

    def "renders dependencies even if the configuration was already resolved"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).publish()
        mavenRepo.module("foo", "bar", 2.0).publish()

        file("build.gradle") << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { foo }
            dependencies {
                foo 'foo:bar:1.0'
                foo 'foo:bar:2.0'
            }

            task resolveConf << {
                configurations.foo.each { println it }
            }
        """

        when:
        run "resolveConf", "dependencies"

        then:
        output.contains "foo:bar:1.0 -> 2.0"
    }

    def "renders selected versions in case of a conflict"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).publish()
        mavenRepo.module("foo", "bar", 2.0).publish()
        mavenRepo.module("foo", "bar", 3.0).dependsOn('foo', 'baz', '5.0').publish()


        mavenRepo.module("foo", "baz", 5.0).publish()

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
        output.contains 'compile - Classpath for compiling the main sources.'

        output.contains(toPlatformLineSeparators("""
+--- root:a:1.0
|    \\--- foo:bar:1.0 -> 3.0
|         \\--- foo:baz:5.0
+--- root:b:1.0
|    \\--- foo:bar:0.5.dont.exist -> 3.0 (*)
+--- root:c:1.0
|    \\--- foo:bar:3.0 (*)
+--- root:d:1.0
|    \\--- foo:bar:2.0 -> 3.0 (*)
\\--- root:e:1.0
     \\--- foo:bar:3.0 (*)
"""))
    }

    def "renders the dependency tree"() {
        given:
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").publish()
        mavenRepo.module("org", "leaf3").publish()
        mavenRepo.module("org", "leaf4").publish()

        mavenRepo.module("org", "middle1").dependsOn('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "middle2").dependsOn('leaf3', 'leaf4').publish()

        mavenRepo.module("org", "toplevel").dependsOn("middle1", "middle2").publish()

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
        output.contains(toPlatformLineSeparators("""
\\--- org:toplevel:1.0
     +--- org:middle1:1.0
     |    +--- org:leaf1:1.0
     |    \\--- org:leaf2:1.0
     \\--- org:middle2:1.0
          +--- org:leaf3:1.0
          \\--- org:leaf4:1.0
"""))
    }

    def "shows selected versions in case of a multi-phase conflict"() {
        given:
        mavenRepo.module("foo", "foo", 1.0).publish()
        mavenRepo.module("foo", "foo", 2.0).publish()
        mavenRepo.module("foo", "foo", 3.0).publish()
        mavenRepo.module("foo", "foo", 4.0).publish()

        mavenRepo.module("bar", "bar", 5.0).dependsOn("foo", "foo", "4.0").publish()
        mavenRepo.module("bar", "bar", 6.0).dependsOn("foo", "foo", "3.0").publish()

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
        output.contains(toPlatformLineSeparators("""
+--- bar:bar:5.0 -> 6.0
|    \\--- foo:foo:3.0
+--- bar:bar:6.0 (*)
+--- foo:foo:1.0 -> 3.0
\\--- foo:foo:2.0 -> 3.0
"""))
    }

    def "deals with dynamic versions with conflicts"() {
        given:
        mavenRepo.module("foo", "bar", 1.0).publish()
        mavenRepo.module("foo", "bar", 2.0).publish()

        mavenRepo.module("foo", "foo", 1.0).dependsOn("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "foo", 2.0).dependsOn("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "foo", 2.5).dependsOn("foo", "bar", "2.0").publish()

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
        output.contains(toPlatformLineSeparators("""
+--- foo:foo:1+ -> 2.5
|    \\--- foo:bar:2.0
\\--- foo:foo:2+ -> 2.5 (*)
"""))
    }

    def "renders ivy tree with custom configurations"() {
        given:
        def module = ivyRepo.module("org", "child")
        module.configurations['first'] = [extendsFrom: ['second'], transitive: true]
        module.configurations['second'] = [extendsFrom: [], transitive: true]
        module.publish()

        module = ivyRepo.module("org", "parent").dependsOn('child')
        module.configurations['first'] = [extendsFrom: ['second'], transitive: true]
        module.configurations['second'] = [extendsFrom: [], transitive: true]
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
        output.contains "org:child:1.0"
    }

    def "renders the ivy tree with conflicts"() {
        given:
        ivyRepo.module("org", "leaf1").publish()
        ivyRepo.module("org", "leaf2").publish()
        ivyRepo.module("org", "leaf3").publish()
        ivyRepo.module("org", "leaf4").publish()
        ivyRepo.module("org", "leaf4", 2.0).publish()

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
        output.contains(toPlatformLineSeparators("""
+--- org:toplevel:1.0
|    +--- org:middle1:1.0
|    |    +--- org:leaf1:1.0
|    |    \\--- org:leaf2:1.0
|    \\--- org:middle2:1.0
|         +--- org:leaf3:1.0
|         \\--- org:leaf4:1.0 -> 2.0
\\--- org:leaf4:2.0
"""))
    }
    def "tells if there are no dependencies"() {
        given:
        buildFile << "configurations { foo }"

        when:
        run "dependencies"

        then:
        output.contains "No dependencies"
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

        mavenRepo.module("org", "toplevel1").dependsOn('leaf1', 'leaf2').publish()
        mavenRepo.module("org", "toplevel2").dependsOn('leaf3', 'leaf4').publish()

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
        output.contains(toPlatformLineSeparators("""
conf2
\\--- org:toplevel2:1.0
     +--- org:leaf3:1.0
     \\--- org:leaf4:1.0
"""))

        !output.contains("conf1")
    }

    @Ignore //TODO exposes NPE problem with dependency report
    void "runtime exception when evaluating action yields decent exception"()
    {
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
        def failure = runAndFail("dependencies", "-s")

        then:
        failure.error.contains("Ka-booom!")
    }
}