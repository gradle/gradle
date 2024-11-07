/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.integtests.resolve.versions

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.hamcrest.CoreMatchers.containsString

class VersionConflictResolutionIntegrationTest extends AbstractIntegrationSpec {
    public static final String CONFLICT_FOUND_HEADER_MESSAGE = 'Conflict found for the following module:'
    private ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve.expectDefaultConfiguration("runtime")
        resolve.addDefaultVariantDerivationStrategy()
    }

    void "strict conflict resolution should fail due to conflict"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url = "${mavenRepo.uri}" }
	}
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		implementation project(':api')
		implementation project(':impl')
	}

	configurations.runtimeClasspath.resolutionStrategy.failOnVersionConflict()
}
"""

        expect:
        runAndFail("tool:dependencies")
        failure.assertThatCause(containsString(CONFLICT_FOUND_HEADER_MESSAGE))
        failure.assertHasResolutions("Run with :tool:dependencyInsight --configuration runtimeClasspath " +
            "--dependency org:foo to get more insight on how to solve the conflict.",
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )
    }

    void "strict conflict resolution should pass when no conflicts"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url = "${mavenRepo.uri}" }
	}
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':tool') {
	dependencies {
		implementation project(':api')
		implementation project(':impl')
	}

	configurations.all { resolutionStrategy.failOnVersionConflict() }
}
"""

        expect:
        run("tool:dependencies")
    }

    void "resolves module version conflicts to the latest version by default"() {
        mavenRepo.module("org", "foo", '1.3.3').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()

        createDirs("api", "impl", "tool")
        settingsFile << """
include 'api', 'impl', 'tool'
"""

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url = "${mavenRepo.uri}" }
	}
}

project(':api') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.3.3')
	}
}

project(':impl') {
	dependencies {
		implementation (group: 'org', name: 'foo', version:'1.4.4')
	}
}

project(':tool') {
	dependencies {
		implementation project(':api')
		implementation project(':impl')
	}
}
"""

        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare()

        when:
        run("tool:checkDeps")

        then:
        resolve.expectGraph {
            root(":tool", "test:tool:") {
                project(":api", "test:api:") {
                    configuration = "runtimeElements"
                    edge("org:foo:1.3.3", "org:foo:1.4.4").byConflictResolution("between versions 1.4.4 and 1.3.3")
                }
                project(":impl", "test:impl:") {
                    configuration = "runtimeElements"
                    module("org:foo:1.4.4").byConflictResolution("between versions 1.4.4 and 1.3.3")
                }
            }
        }
    }

    void "resolves transitive module version conflicts to the latest version by default"() {
        def foo133 = mavenRepo.module("org", "foo", '1.3.3').publish()
        def foo144 = mavenRepo.module("org", "foo", '1.4.4').publish()
        mavenRepo.module("org", "bar", "1.0").dependsOn(foo133).publish()
        mavenRepo.module("org", "baz", "1.0").dependsOn(foo144).publish()

        buildFile << """
apply plugin: 'java'
group = 'org'
version = '1.0'
repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation (group: 'org', name: 'bar', version:'1.0')
    implementation (group: 'org', name: 'baz', version:'1.0')
}

task resolve {
    doLast {
        println configurations.compile.files
    }
}
"""

        def resolve = new ResolveTestFixture(buildFile).expectDefaultConfiguration("runtime")
        resolve.prepare()

        when:
        run(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", "org:test:1.0") {
                module("org:bar:1.0") {
                    edge("org:foo:1.3.3", "org:foo:1.4.4").byConflictResolution("between versions 1.4.4 and 1.3.3")
                }
                module("org:baz:1.0") {
                    module("org:foo:1.4.4").byConflictResolution("between versions 1.4.4 and 1.3.3")
                }
            }
        }
    }

    void "re-selects target version for previously resolved then evicted selector"() {
        def depOld = mavenRepo.module("org", "dep", "2.0").publish()
        def depNew = mavenRepo.module("org", "dep", "2.5").publish()

        def controlOld = mavenRepo.module("org", "control", "1.0").dependsOn(depNew).publish()
        def controlNew = mavenRepo.module("org", "control", "1.2").dependsOn(depNew).publish()
        def controlNewBringer = mavenRepo.module("org", "control-1.2-bringer", "1.0").dependsOn(controlNew).publish()

        mavenRepo.module("org", "one", "1.0").dependsOn(controlOld).publish()

        def depOldBringer = mavenRepo.module("org", "dep-2.0-bringer", "1.0").dependsOn(depOld).publish()
        // Note: changing the order of the following dependencies makes the test pass
        mavenRepo.module("org", "two", "1.0").dependsOn(controlNewBringer).dependsOn(depOldBringer).publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:one:1.0'
    compile 'org:two:1.0'
}
"""

        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:one:1.0") {
                    edge("org:control:1.0", "org:control:1.2") {
                        byConflictResolution("between versions 1.2 and 1.0")
                        module("org:dep:2.5")
                    }
                }
                module("org:two:1.0") {
                    module("org:dep-2.0-bringer:1.0") {
                        edge("org:dep:2.0", "org:dep:2.5") {
                            byConflictResolution("between versions 2.5 and 2.0")
                        }
                    }
                    module("org:control-1.2-bringer:1.0") {
                        module("org:control:1.2")
                    }
                }
            }
        }
    }

    void "does not attempt to resolve an evicted dependency"() {
        mavenRepo.module("org", "external", "1.2").publish()
        mavenRepo.module("org", "dep", "2.2").dependsOn("org", "external", "1.0").publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}
"""
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:external:1.2").byConflictResolution("between versions 1.2 and 1.0")
                module("org:dep:2.2") {
                    edge("org:external:1.0", "org:external:1.2")
                }
            }
        }
    }

    @Issue("GRADLE-2890")
    void "selects latest from multiple conflicts"() {
        mavenRepo.module("org", "child", '1').publish()
        mavenRepo.module("org", "child", '2').publish()
        mavenRepo.module("org", "parent", '1').dependsOn("org", "child", "1").publish()
        mavenRepo.module("org", "parent", '2').dependsOn("org", "child", "2").publish()
        mavenRepo.module("org", "dep", '2').dependsOn("org", "parent", "2").publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}
configurations {
    compile
}
dependencies {
    compile 'org:parent:1'
    compile 'org:child:2'
    compile 'org:dep:2'
}
"""
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:child:2") {
                    byConflictResolution("between versions 2 and 1")
                }
                edge("org:parent:1", "org:parent:2") {
                    byConflictResolution("between versions 2 and 1")
                    module("org:child:2")
                }
                module("org:dep:2") {
                    module("org:parent:2")
                }
            }
        }
    }

    void "resolves dynamic dependency before resolving conflict"() {
        mavenRepo.module("org", "external", "1.2").publish()
        mavenRepo.module("org", "external", "1.4").publish()
        mavenRepo.module("org", "dep", "2.2").dependsOn("org", "external", "[1.3,)").publish()

        def buildFile = file("build.gradle")
        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}
"""
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:external:1.2", "org:external:1.4") {
                    byConflictResolution("between versions 1.2 and 1.4")
                }
                module("org:dep:2.2") {
                    edge("org:external:[1.3,)", "org:external:1.4")
                }
            }
        }
    }

    void "fails when version selected by conflict resolution does not exist"() {
        mavenRepo.module("org", "external", "1.2").publish()
        mavenRepo.module("org", "dep", "2.2").dependsOn("org", "external", "1.4").publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}

task checkDeps {
    def files = configurations.compile
    doLast {
        files.files
    }
}
"""

        expect:
        runAndFail("checkDeps")
        failure.assertHasCause("Could not find org:external:1.4.")
    }

    void "does not fail when evicted version does not exist"() {
        mavenRepo.module("org", "external", "1.4").publish()
        mavenRepo.module("org", "dep", "2.2").dependsOn("org", "external", "1.4").publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}

configurations { compile }

dependencies {
    compile 'org:external:1.2'
    compile 'org:dep:2.2'
}
"""
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:external:1.2", "org:external:1.4") {
                    byConflictResolution("between versions 1.4 and 1.2")
                }
                module("org:dep:2.2") {
                    module("org:external:1.4")
                }
            }
        }
    }

    void "takes newest dynamic version when dynamic version forced"() {
        mavenRepo.module("org", "foo", '1.3.0').publish()

        mavenRepo.module("org", "foo", '1.4.1').publish()
        mavenRepo.module("org", "foo", '1.4.4').publish()
        mavenRepo.module("org", "foo", '1.4.9').publish()

        mavenRepo.module("org", "foo", '1.6.0').publish()

        createDirs("api", "impl", "tool")
        settingsFile << "include 'api', 'impl', 'tool'"

        buildFile << """
allprojects {
	apply plugin: 'java'
	repositories {
		maven { url = "${mavenRepo.uri}" }
	}
}

project(':api') {
	dependencies {
		implementation 'org:foo:1.4.4'
	}
}

project(':impl') {
	dependencies {
		implementation 'org:foo:1.4.1'
	}
}

project(':tool') {

	dependencies {
		implementation project(':api'), project(':impl'), 'org:foo:1.3.0'
	}

	configurations.all {
	    resolutionStrategy {
	        force 'org:foo:[1.4, 1.5)'
	        failOnVersionConflict()
	    }
	}
}

"""
        resolve.prepare("runtimeClasspath")

        when:
        run("tool:checkDeps")

        then:
        resolve.expectGraph {
            root(":tool", "test:tool:") {
                project(":api", "test:api:") {
                    edge("org:foo:1.4.4", "org:foo:1.4.9") {
                        forced()
                        byReason("didn't match version 1.6.0")
                    }
                }
                project(":impl", "test:impl:") {
                    edge("org:foo:1.4.1", "org:foo:1.4.9")
                }
                edge("org:foo:1.3.0", "org:foo:1.4.9")
            }
        }
    }

    void "parent pom does not participate in forcing mechanism"() {
        mavenRepo.module("org", "foo", '1.3.0').publish()
        mavenRepo.module("org", "foo", '2.4.0').publish()

        def parent = mavenRepo.module("org", "someParent", "1.0")
        parent.type = 'pom'
        parent.dependsOn("org", "foo", "1.3.0")
        parent.publish()

        def otherParent = mavenRepo.module("org", "someParent", "2.0")
        otherParent.type = 'pom'
        otherParent.dependsOn("org", "foo", "2.4.0")
        otherParent.publish()

        def dep = mavenRepo.module("org", "someArtifact", '1.0')
        dep.parent("org", "someParent", "1.0")
        dep.publish()

        buildFile << """
apply plugin: 'java'
repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation 'org:someArtifact:1.0'
}

configurations.all {
    resolutionStrategy {
        force 'org:someParent:2.0'
        failOnVersionConflict()
    }
}
"""
        resolve.prepare("runtimeClasspath")

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:someArtifact:1.0") {
                    module("org:foo:1.3.0")
                }
            }
        }
    }

    void "previously evicted nodes should contain correct target version"() {
        /*
        a1->b1
        a2->b2->a1

        resolution process:

        1. stop resolution, resolve conflict a1 vs a2
        2. select a2, restart resolution
        3. stop, resolve b1 vs b2
        4. select b2, restart
        5. resolve b2 dependencies, a1 has been evicted previously but it should show correctly on the report
           ('dependencies' report pre 1.2 would not show the a1 dependency leaf for this scenario)
        */

        ivyRepo.module("org", "b", '1.0').publish()
        ivyRepo.module("org", "a", '1.0').dependsOn("org", "b", '1.0').publish()
        ivyRepo.module("org", "b", '2.0').dependsOn("org", "a", "1.0").publish()
        ivyRepo.module("org", "a", '2.0').dependsOn("org", "b", '2.0').publish()

        buildFile << """
            repositories {
                ivy { url = "${ivyRepo.uri}" }
            }

            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:a:2.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0")
                module("org:a:2.0") {
                    configuration = "default"
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:b:2.0") {
                        edge("org:a:1.0", "org:a:2.0")
                    }
                }
            }
        }
    }

    @Issue("GRADLE-2555")
    void "can deal with transitive with parent in conflict"() {
        /*
            Graph looks like…

            \--- org:a:1.0
                 \--- org:in-conflict:1.0 -> 2.0
                      \--- org:target:1.0
                           \--- org:target-child:1.0
            \--- org:b:1.0
                 \--- org:b-child:1.0
                      \--- org:in-conflict:2.0 (*)

            This is the simplest structure I could boil it down to that produces the error.
            - target *must* have a child
            - Having "b" depend directly on "in-conflict" does not produce the error, needs to go through "b-child"
         */

        mavenRepo.module("org", "target-child", "1.0").publish()
        mavenRepo.module("org", "target", "1.0").dependsOn("org", "target-child", "1.0").publish()
        mavenRepo.module("org", "in-conflict", "1.0").dependsOn("org", "target", "1.0").publish()
        mavenRepo.module("org", "in-conflict", "2.0").dependsOn("org", "target", "1.0").publish()

        mavenRepo.module("org", "a", '1.0').dependsOn("org", "in-conflict", "1.0").publish()

        mavenRepo.module("org", "b-child", '1.0').dependsOn("org", "in-conflict", "2.0").publish()

        mavenRepo.module("org", "b", '1.0').dependsOn("org", "b-child", "1.0").publish()

        buildFile << """
            repositories { maven { url = "${mavenRepo.uri}" } }

            configurations { compile }

            dependencies {
                compile "org:a:1.0", "org:b:1.0"
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:in-conflict:1.0", "org:in-conflict:2.0") {
                        byConflictResolution("between versions 2.0 and 1.0")
                    }
                }
                module("org:b:1.0") {
                    module("org:b-child:1.0") {
                        module("org:in-conflict:2.0") {
                            module("org:target:1.0") {
                                module("org:target-child:1.0")
                            }
                        }
                    }
                }
            }
        }
    }

    @Issue("GRADLE-2555")
    void "batched up conflicts with conflicted parent and child"() {
        /*
        Dependency tree:

        a->c1
        b->c2->x1
        d->x1
        f->x2

        Everything is resolvable but not x2

        Scenario:
         - We have batched up conflicts
         - root of one conflicted version is also conflicted
         - conflicted root is positioned on the conflicts queue after the conflicted child (the order of declaring dependencies matters)
         - winning root depends on a child that previously was evicted
         - finally, the winning child is an unresolved dependency
        */
        mavenRepo.module("org", "c", '1.0').publish()
        mavenRepo.module("org", "x", '1.0').publish()
        mavenRepo.module("org", "c", '2.0').dependsOn("org", "x", '1.0').publish()
        mavenRepo.module("org", "a").dependsOn("org", "c", "1.0").publish()
        mavenRepo.module("org", "b").dependsOn("org", "c", "2.0").publish()
        mavenRepo.module("org", "d").dependsOn("org", "x", "1.0").publish()
        mavenRepo.module("org", "f").dependsOn("org", "x", "2.0").publish()

        buildFile << """
            repositories { maven { url = "${mavenRepo.uri}" } }
            configurations {
                childFirst
                parentFirst
            }
            dependencies {
                //conflicted child is resolved first
                childFirst 'org:d:1.0', 'org:f:1.0', 'org:a:1.0', 'org:b:1.0'
                //conflicted parent is resolved first
                parentFirst 'org:a:1.0', 'org:b:1.0', 'org:d:1.0', 'org:f:1.0'
            }
        """

        when:
        run("dependencies")

        then:
        output.contains """
childFirst
+--- org:d:1.0
|    \\--- org:x:1.0 -> 2.0 FAILED
+--- org:f:1.0
|    \\--- org:x:2.0 FAILED
+--- org:a:1.0
|    \\--- org:c:1.0 -> 2.0
|         \\--- org:x:1.0 -> 2.0 FAILED
\\--- org:b:1.0
     \\--- org:c:2.0 (*)

parentFirst
+--- org:a:1.0
|    \\--- org:c:1.0 -> 2.0
|         \\--- org:x:1.0 -> 2.0 FAILED
+--- org:b:1.0
|    \\--- org:c:2.0 (*)
+--- org:d:1.0
|    \\--- org:x:1.0 -> 2.0 FAILED
\\--- org:f:1.0
     \\--- org:x:2.0 FAILED"""
    }

    @Issue("GRADLE-2752")
    void "selects root module when earlier version of module requested"() {
        mavenRepo.module("org", "test", "1.2").publish()
        mavenRepo.module("org", "other", "1.7").dependsOn("org", "test", "1.2").publish()

        buildFile << """
apply plugin: 'java'

group = "org"
version = "1.3"

repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation "org:other:1.7"
}
"""

        def resolve = new ResolveTestFixture(buildFile).expectDefaultConfiguration("runtime")
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", "org:test:1.3") {
                module("org:other:1.7") {
                    edge("org:test:1.2", "org:test:1.3")
                }
            }
        }
    }

    @Issue("GRADLE-2920")
    void "selects later version of root module when requested"() {
        mavenRepo.module("org", "test", "2.1").publish()
        mavenRepo.module("org", "other", "1.7").dependsOn("org", "test", "2.1").publish()

        buildFile << """
apply plugin: 'java'

group = "org"
version = "1.3"

repositories {
    maven { url = "${mavenRepo.uri}" }
}

dependencies {
    implementation "org:other:1.7"
}
"""

        def resolve = new ResolveTestFixture(buildFile).expectDefaultConfiguration("runtime")
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", "org:test:1.3") {
                module("org:other:1.7") {
                    module("org:test:2.1").byConflictResolution("between versions 2.1 and 1.3")
                }
            }
        }
    }

    void "module is required only by selected conflicting version and in turn requires evicted conflicting version"() {
        /*
            a2 -> b1 -> c1
            a1
            c2
         */
        mavenRepo.module("org", "a", "1").publish()
        mavenRepo.module("org", "a", "2").dependsOn("org", "b", "1").publish()
        mavenRepo.module("org", "b", "1").dependsOn("org", "c", "1").publish()
        mavenRepo.module("org", "c", "1").publish()
        mavenRepo.module("org", "c", "2").publish()

        buildFile << """
repositories {
    maven { url = "${mavenRepo.uri}" }
}
configurations {
    compile
}
dependencies {
    compile "org:a:2"
    compile "org:a:1"
    compile "org:c:2"
}
"""
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:2") {
                    byConflictResolution("between versions 2 and 1")
                }
                edge("org:a:1", "org:a:2") {
                    module("org:b:1") {
                        edge("org:c:1", "org:c:2") {
                            byConflictResolution("between versions 2 and 1")
                        }
                    }
                }
                module("org:c:2")
            }
        }
    }

    @Issue("GRADLE-2738")
    def "resolution fails when any selector cannot be resolved"() {
        given:
        //only 1.5 published:
        mavenRepo.module("org", "leaf", "1.5").publish()

        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "(,1.0)").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "1.0").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[1.5,1.9]").publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", "leaf", "2.0+").publish()

        buildFile << """
            version = 12
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:a:1.0', 'org:b:1.0', 'org:c:1.0', 'org:d:1.0'
            }
            task resolve {
                def files = configurations.conf
                doLast {
                    files.files
                }
            }
            task checkGraph {
                def result = configurations.conf.incoming.resolutionResult.rootComponent
                doLast {
                    def root = result.get()
                    def components = root.dependencies.collect { it.selected }
                    def a = components.find { it.id instanceof ModuleComponentIdentifier && it.id.module == 'a' }
                    def b = components.find { it.id instanceof ModuleComponentIdentifier && it.id.module == 'b' }
                    def c = components.find { it.id instanceof ModuleComponentIdentifier && it.id.module == 'c' }
                    def d = components.find { it.id instanceof ModuleComponentIdentifier && it.id.module == 'd' }

                    a.dependencies.each {
                        assert it instanceof UnresolvedDependencyResult
                        assert it.requested.toString() == 'org:leaf:(,1.0)'
                        assert it.failure.getMessage().startsWith('Could not find any version that matches org:leaf:(,1.0).')
                    }
                    b.dependencies.each {
                        assert it instanceof ResolvedDependencyResult
                        assert it.requested.toString() == 'org:leaf:1.0'
                        assert it.selected.id.module == 'leaf'
                    }
                    c.dependencies.each {
                        assert it instanceof ResolvedDependencyResult
                        assert it.requested.toString() == 'org:leaf:[1.5,1.9]'
                        assert it.selected.id.module == 'leaf'
                    }
                    d.dependencies.each {
                        assert it instanceof UnresolvedDependencyResult
                        assert it.requested.toString() == 'org:leaf:2.0+'
                        assert it.failure.getMessage().startsWith('Could not find any version that matches org:leaf:2.0+.')
                    }
                }
            }
        """

        when:
        succeeds "checkGraph"

        and:
        runAndFail "resolve"

        then:
        failure.assertResolutionFailure(":conf").assertFailedDependencyRequiredBy("root project : > org:d:1.0")
    }

    def "chooses highest version that is included in both ranges"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:6") {
                        byReason("didn't match versions 10, 9, 8, 7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[4,8]", "org:leaf:6")
                }
            }
        }
    }

    def "chooses highest version that is included in both ranges, with the highest version in the intersection missing"() {
        given:
        (1..10).findAll {
            // We skip v6, as we test what happens when the top version of the intersection is missing
            it != 6
        }.each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:5") {
                        byReason("didn't match versions 10, 9, 8, 7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[4,8]", "org:leaf:5")
                }
            }
        }
    }

    def "chooses highest version that is included in both ranges when fail on conflict is set"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile {
                    resolutionStrategy {
                        failOnVersionConflict()
                    }
                }
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:6") {
                        byReason("didn't match versions 10, 9, 8, 7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[4,8]", "org:leaf:6")
                }
            }
        }
    }

    def "chooses highest version that is included in all ranges"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[3,5]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:5") {
                        byReason("didn't match versions 10, 9, 8, 7")
                        byReason("didn't match versions 10, 9, 8, 7, 6")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[4,8]", "org:leaf:5")
                }
                module("org:c:1.0") {
                    edge("org:leaf:[3,5]", "org:leaf:5")
                }
            }
        }
    }

    def "chooses highest version that is included in all ranges, when dependencies are included at different transitivity levels"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        // top level
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()

        // b will include 'leaf' through a transitive dependency
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "b2", "1.0").publish()
        mavenRepo.module("org", "b2", "1.0").dependsOn("org", "leaf", "[3,5]").publish()

        // c will include 'leaf' through a 2d level transitive dependency
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "c2", "1.0").publish()
        mavenRepo.module("org", "c2", "1.0").dependsOn("org", "c3", "1.0").publish()
        mavenRepo.module("org", "c3", "1.0").dependsOn("org", "leaf", "[3,5]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:5") {
                        notRequested()
                        byReason("didn't match versions 10, 9, 8, 7")
                        byReason("didn't match versions 10, 9, 8, 7, 6")
                    }
                }
                module("org:b:1.0") {
                    module("org:b2:1.0") {
                        edge("org:leaf:[3,5]", "org:leaf:5")
                    }
                }
                module("org:c:1.0") {
                    module("org:c2:1.0") {
                        module("org:c3:1.0") {
                            edge("org:leaf:[3,5]", "org:leaf:5")
                        }
                    }
                }
            }
        }
    }

    def "upgrades version when ranges are disjoint"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,3]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[5,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,3]", "org:leaf:8") {
                        notRequested()
                        byReason("didn't match versions 10, 9")
                        byReason("didn't match versions 10, 9, 8, 7, 6, 5, 4")
                        byConflictResolution("between versions 3 and 8")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[5,8]", "org:leaf:8")
                }
            }
        }
    }

    def "fail when ranges are disjoint and no top range artifact is present"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[1,5]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[11,15]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                conf 'org:a:1.0', 'org:b:1.0'
            }
            task checkDeps {
                def files = configurations.conf
                doLast {
                    files.files
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertThatCause(containsString("Could not find any version that matches org:leaf:[11,15]."))
    }

    def "upgrades version when ranges are disjoint unless failOnVersionConflict is set"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,3]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[5,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy.failOnVersionConflict()
                }
            }
            dependencies {
                conf 'org:a:1.0', 'org:b:1.0'
            }
            task checkDeps {
                def files = configurations.conf
                doLast {
                    files.files
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertThatCause(containsString(CONFLICT_FOUND_HEADER_MESSAGE))
    }

    def "upgrades version when one of the ranges is disjoint"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[3,4]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[7,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:8") {
                        notRequested()
                        byReason("didn't match versions 10, 9")
                        byReason("didn't match versions 10, 9, 8, 7")
                        byReason("didn't match versions 10, 9, 8, 7, 6, 5")
                        byConflictResolution("between versions 8 and 4")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[3,4]", "org:leaf:8")
                }
                module("org:c:1.0") {
                    edge("org:leaf:[7,8]", "org:leaf:8")
                }
            }
        }
    }

    def "fails when one of the ranges is disjoint and fail on conflict is set"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[3,4]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[7,8]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                    resolutionStrategy {
                        failOnVersionConflict()
                    }
                }
            }
            dependencies {
                conf 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
            task checkDeps {
                def files = configurations.conf
                doLast {
                    files.files
                }
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertThatCause(containsString(CONFLICT_FOUND_HEADER_MESSAGE))
    }

    def "chooses highest version of all versions fully included within range"() {
        given:
        (1..12).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[1,12]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[3,8]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[2,10]").publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", "leaf", "[4,7]").publish()
        mavenRepo.module("org", "e", "1.0").dependsOn("org", "leaf", "[4,11]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0', 'org:d:1.0', 'org:e:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[1,12]", "org:leaf:7") {
                        byReason("didn't match versions 12, 11")
                        byReason("didn't match versions 12, 11, 10, 9")
                        byReason("didn't match versions 12, 11, 10, 9, 8")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[3,8]", "org:leaf:7")
                }
                module("org:c:1.0") {
                    edge("org:leaf:[2,10]", "org:leaf:7")
                }
                module("org:d:1.0") {
                    edge("org:leaf:[4,7]", "org:leaf:7")
                }
                module("org:e:1.0") {
                    edge("org:leaf:[4,11]", "org:leaf:7")
                }
            }
        }
    }

    def "selects the minimal version when in there's an open range"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[5,)").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:6") {
                        byReason("didn't match versions 10, 9, 8, 7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[5,)", "org:leaf:6")
                }
            }
        }
    }

    def "range selector should not win over sub-version selector"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "1.$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[1.2,1.6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "1.+").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[1.2,1.6]", "org:leaf:1.10") {
                        byConflictResolution("between versions 1.6 and 1.10")
                        byReason("didn't match versions 1.10, 1.9, 1.8, 1.7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:1.+", "org:leaf:1.10")
                }
            }
        }
    }

    def "range conflict resolution not interfering between distinct configurations"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[3,5]").publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", "leaf", "8").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
                conf2
                conf3
                conf4
            }
            dependencies {
                conf 'org:a:1.0', 'org:b:1.0'
                conf2 'org:a:1.0', 'org:c:1.0'
                conf3 'org:b:1.0', 'org:c:1.0'
                conf4 'org:b:1.0', 'org:c:1.0', 'org:d:1.0'
            }
            task checkDeps {
                def files1 = configurations.conf
                def files2 = configurations.conf2
                def files3 = configurations.conf3
                def files4 = configurations.conf4
                doLast {
                    def files = files1*.name.sort()
                    assert files == ['a-1.0.jar', 'b-1.0.jar', 'leaf-6.jar']
                    files = files2*.name.sort()
                    assert files == ['a-1.0.jar', 'c-1.0.jar', 'leaf-5.jar']
                    files = files3*.name.sort()
                    assert files == ['b-1.0.jar', 'c-1.0.jar', 'leaf-5.jar']
                    files = files4*.name.sort()
                    assert files == ['b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'leaf-8.jar']
                }
            }
        """

        when:
        run 'checkDeps'

        then:
        noExceptionThrown()
    }

    def "conflict resolution on different dependencies are handled separately"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "leaf", "$it").publish()
            mavenRepo.module("org", "leaf2", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "leaf", "[2,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "leaf", "[4,8]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf2", "[3,4]").publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", "leaf2", "[1,7]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0', 'org:d:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[2,6]", "org:leaf:6") {
                        byReason("didn't match versions 10, 9, 8, 7")
                    }
                }
                module("org:b:1.0") {
                    edge("org:leaf:[4,8]", "org:leaf:6")
                }
                module("org:c:1.0") {
                    edge("org:leaf2:[3,4]", "org:leaf2:4") {
                        byReason("didn't match versions 10, 9, 8, 7, 6, 5")
                    }
                }
                module("org:d:1.0") {
                    edge("org:leaf2:[1,7]", "org:leaf2:4")
                }
            }
        }
    }

    def "previously selected transitive dependency is not used when it becomes orphaned because of selection of a different version of its dependent module"() {
        given:
        (1..10).each {
            def dep = mavenRepo.module('org', 'zdep', "$it").publish()
            mavenRepo.module("org", "leaf", "$it").dependsOn(dep).publish()
        }
        mavenRepo.module("org", "a", "1.0")
            .dependsOn("org", "c", "1.0")
            .dependsOn('org', 'leaf', '[1,8]')
            .publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "c", "1.1").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "leaf", "[3,4]").publish()
        mavenRepo.module("org", "c", "1.1").dependsOn("org", "leaf", "[4,6]").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1.0") {
                    edge("org:leaf:[1,8]", "org:leaf:6") {
                        notRequested()
                        byReason("didn't match versions 10, 9")
                        byReason("didn't match versions 10, 9, 8, 7")
                        module("org:zdep:6")
                    }
                    edge("org:c:1.0", "org:c:1.1") {
                        byConflictResolution("between versions 1.1 and 1.0")
                        edge("org:leaf:[4,6]", "org:leaf:6")
                    }
                }
                module("org:b:1.0") {
                    module("org:c:1.1")
                }
            }
        }
    }

    def "evicted version removes range constraint from transitive dependency"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "e", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "e", "[3,6]").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "e", "[1,10]").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", "e", "[4,8]").publish()


        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    edge("org:e:[4,8]", "org:e:8") {
                        byReason("didn't match versions 10, 9")
                    }
                }
                module("org:b:1.0") {
                    edge("org:e:[1,10]", "org:e:8")
                }
                module("org:c:1.0") {
                    module("org:a:2.0")
                }
            }
        }
    }

    def "orphan node can be re-selected later with a non short-circuiting selector"() {
        given:
        (1..10).each {
            mavenRepo.module("org", "e", "$it").publish()
        }
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "e", "10").publish()
        mavenRepo.module("org", "b", "1.0").dependsOn("org", "a", "2.0").publish()
        mavenRepo.module("org", "a", "2.0").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn("org", "d", "1.0").publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", "e", "latest.release").publish()


        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:b:1.0', 'org:c:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                }
                module("org:b:1.0") {
                    module("org:a:2.0")
                }
                module("org:c:1.0") {
                    module("org:d:1.0") {
                        edge("org:e:latest.release", "org:e:10")
                    }
                }
            }
        }
    }

    def "presence of evicted and orphan node for a module do not fail selection"() {
        given:
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "b", "1.0").publish()
        mavenRepo.module("org", "b", "1.0").publish()
        mavenRepo.module("org", "b", "2.0").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn('org', 'b', '2.0').publish()
        mavenRepo.module("org", "d", "1.0").dependsOn("org", 'e', '1.0').publish()
        mavenRepo.module("org", "e", "1.0").dependsOn("org", 'a', '2.0').dependsOn('org', 'c', '1.0').publish()
        mavenRepo.module("org", "a", "2.0").dependsOn("org", 'c', '2.0').publish()
        mavenRepo.module('org', 'c', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:c:1.0', 'org:d:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                    module("org:c:2.0") {
                        byConflictResolution("between versions 2.0 and 1.0")
                    }
                }
                edge("org:c:1.0", "org:c:2.0")
                module("org:d:1.0") {
                    module("org:e:1.0") {
                        module("org:a:2.0")
                        edge("org:c:1.0", "org:c:2.0")
                    }
                }
            }
        }
    }

    def "can have a dependency on evicted node"() {
        given:
        mavenRepo.module("org", "a", "1.0").dependsOn("org", "b", "1.0").publish()
        mavenRepo.module("org", "b", "1.0").publish()
        mavenRepo.module("org", "c", "1.0").dependsOn('org', 'a', '1.0').publish()
        mavenRepo.module('org', 'd', '1.0').dependsOn('org', 'a', '2.0').publish()
        mavenRepo.module('org', 'a', '2.0').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1.0', 'org:c:1.0', 'org:d:1.0'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1.0", "org:a:2.0") {
                    byConflictResolution("between versions 2.0 and 1.0")
                }
                module("org:c:1.0") {
                    edge("org:a:1.0", "org:a:2.0")
                }
                module("org:d:1.0") {
                    module("org:a:2.0")
                }
            }
        }
    }

    def "evicted hard dependency shouldn't add constraint on range"() {
        given:
        4.times { mavenRepo.module("org", "e", "${it + 1}").publish() }
        4.times { mavenRepo.module("org", "a", "${it + 1}").dependsOn('org', 'e', "${it + 1}").publish() }
        mavenRepo.module("org", "b", "1").dependsOn('org', 'a', '4').publish() // this will be evicted
        mavenRepo.module('org', 'c', '1').dependsOn('org', 'd', '1').publish()
        mavenRepo.module('org', 'd', '1').dependsOn('org', 'b', '2').publish()
        mavenRepo.module('org', 'b', '2').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:[1,3]', 'org:b:1', 'org:c:1'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:[1,3]", "org:a:3") {
                    notRequested()
                    byReason("didn't match version 4")
                    module("org:e:3")
                }
                edge("org:b:1", "org:b:2") {
                    byConflictResolution("between versions 2 and 1")
                }
                module("org:c:1") {
                    module("org:d:1") {
                        module("org:b:2")
                    }
                }
            }
        }
    }

    def "evicted hard dependency shouldn't add constraint on version"() {
        given:
        mavenRepo.module("org", "a", "1").publish()
        mavenRepo.module("org", "a", "4").publish()
        mavenRepo.module("org", "b", "1").dependsOn('org', 'a', '4').publish() // this will be evicted
        mavenRepo.module('org', 'b', '2').publish()
        mavenRepo.module('org', 'c', '1').dependsOn('org', 'd', '1').publish()
        mavenRepo.module('org', 'd', '1').dependsOn('org', 'b', '2').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1', 'org:b:1', 'org:c:1'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:a:1")
                edge("org:b:1", "org:b:2") {
                    byConflictResolution("between versions 2 and 1")
                }
                module("org:c:1") {
                    module("org:d:1") {
                        module("org:b:2")
                    }
                }
            }
        }
    }

    def "doesn't include evicted version from branch which has been deselected"() {
        given:
        mavenRepo.module('org', 'a', '1').dependsOn('org', 'b', '2').publish()
        mavenRepo.module('org', 'b', '1').publish()
        mavenRepo.module('org', 'b', '2').publish()
        mavenRepo.module('org', 'c', '1').dependsOn('org', 'a', '2').publish()
        mavenRepo.module('org', 'a', '2').publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                compile
            }
            dependencies {
                compile 'org:a:1', 'org:b:1', 'org:c:1'
            }
        """
        resolve.prepare()

        when:
        run("checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:a:1", "org:a:2") {
                    byConflictResolution("between versions 2 and 1")
                }
                module("org:b:1")
                module("org:c:1") {
                    module("org:a:2")
                }
            }
        }
    }

    def 'order of dependency declaration does not effect transitive dependency versions'() {
        given:
        def foo11 = mavenRepo.module('org', 'foo', '1.1').publish()
        def foo12 = mavenRepo.module('org', 'foo', '1.2').publish()
        def baz11 = mavenRepo.module('org', 'baz', '1.1').dependsOn(foo11).publish()
        mavenRepo.module('org', 'baz', '1.2').dependsOn(foo12).publish()
        mavenRepo.module('org', 'bar', '1.1').dependsOn(baz11).publish()

        ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf").expectDefaultConfiguration("runtime")
        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
            dependencies {
                if ($barFirst) {
                    conf 'org:bar:1.1' // WORKS IF THIS DEPENDENCY IS FIRST
                }
                conf 'org:baz:[1.0,2.0)'
                if (!$barFirst) {
                    conf 'org:bar:1.1' // FAILED IF HERE
                }
                conf 'org:foo:[1.0,2.0)'
            }
"""
        resolve.prepare()

        when:
        run 'dependencies', 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:bar:1.1') {
                    module('org:baz:1.1') {
                        module('org:foo:1.1')
                    }
                }
                edge("org:foo:[1.0,2.0)", 'org:foo:1.1')
                edge('org:baz:[1.0,2.0)', 'org:baz:1.1')
            }
        }

        where:
        barFirst << [false, true]
    }

    @Issue("gradle/gradle-private#1268")
    def "shouldn't fail if root component is also added through cycle, and that failOnVersionConflict() is used"() {
        settingsFile << """
            include "testlib", "common"
        """

        buildFile << """
            subprojects {
                apply plugin: 'java-library'
                configurations.all {
                   resolutionStrategy.failOnVersionConflict()
                }
            }
        """

        file("testlib/build.gradle") << """
            dependencies {
                api project(':common') // cycle causes resolution to fail, but shouldn't
            }
        """

        file("common/build.gradle") << """
            dependencies {
                testImplementation project(':testlib')
            }
        """

        when:
        run 'common:dependencies', '--configuration', 'testCompileClasspath'

        then:
        noExceptionThrown()
    }

    @Issue("gradle/gradle#6403")
    def "shouldn't fail when forcing a dynamic version in resolution strategy"() {

        given:
        mavenRepo.module("org", "moduleA", "1.1").publish()

        buildFile << """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            configurations {
                conf {
                   resolutionStrategy {
                      force "org:moduleA:1.+"
                      failOnVersionConflict()
                   }
                }
            }

            dependencies {
               conf("org:moduleA:1.+")
               conf("org:moduleA:1.1")
            }
        """

        when:
        run 'dependencies', '--configuration', 'conf'

        then:
        noExceptionThrown()


    }

    def 'optional dependency marked as no longer pending reverts to pending if hard edge disappears (remover has constraint: #dependsOptional, root has constraint: #constraintsOptional)'() {
        given:
        def optional = mavenRepo.module('org', 'optional', '1.0').publish()
        def main = mavenRepo.module('org', 'main', '1.0').dependsOn(optional, optional: true).publish()
        mavenRepo.module('org.a', 'root', '1.0').dependsOn(main).dependsOn(optional).publish()
        def root11 = mavenRepo.module('org.a', 'root', '1.1').dependsOn(main).publish()
        def bom = mavenRepo.module("org", "bom", "1.0")
        bom.hasPackaging('pom')
        bom.dependencyConstraint(root11)
        if (dependsOptional) {
            bom.dependencyConstraint(optional)
        }
        bom.publish()

        buildFile << """
apply plugin: 'java'

repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}

dependencies {
    implementation 'org.a:root'
    implementation platform('org:bom:1.0')
    constraints {
        implementation 'org.a:root:1.0'
        if ($constraintsOptional) {
            implementation 'org:optional:1.0'
        }
    }
}
"""
        when:
        succeeds 'dependencies', '--configuration', 'compileClasspath'

        then:
        outputDoesNotContain('org:optional')

        where:
        dependsOptional | constraintsOptional
        true            | true
        true            | false
        false           | true
        false           | false
    }

    @Issue("gradle/gradle#8944")
    def 'verify that cleaning up constraints no longer causes a ConcurrentModificationException'() {
        given:
        // Direct dependency with transitive to be substituted by project
        def project = mavenRepo.module('org', 'project', '1.0')
        mavenRepo.module('org', 'direct', '1.0').dependsOn(project).publish()

        // Updated version no longer depends on project
        def updated = mavenRepo.module('org', 'direct', '1.1').publish()

        // Chain of deps to make sure upgrade happens after substituting and finding deps
        def b = mavenRepo.module('org', 'b', '1.0').dependsOn(updated).publish()
        mavenRepo.module('org', 'a', '1.0').dependsOn(b).publish()

        mavenRepo.module('org', 'lib', '1.0').publish()
        mavenRepo.module('org', 'other', '1.0').publish()

        createDirs("sub")
        settingsFile << """
include 'sub'
"""

        buildFile << """
apply plugin: 'java'

repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute module('org:project') using project(':sub')
    }
}

dependencies {
    implementation 'org:direct:1.0'
    implementation 'org:a:1.0'
}

project(':sub') {
    apply plugin: 'java'

    group = 'org'
    version = '1.0'

    dependencies {
        constraints {
            implementation 'org:lib:1.0'
        }

        implementation 'org:lib'
        implementation 'org:other:1.0'
    }
}
"""
        expect:
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'
    }

    @Issue("gradle/gradle#11844")
    def 'does not fail serialization in recursive error case'() {
        // org:lib:1.0 -> org:between:1.0 -> org:lib:1.1
        //
        //  - org:lib:1.1 is selected
        //  - removes org:between:1.0
        //  - org:lib:1.1 stays selected (because of cycle), still internal state is updated partially and org:lib:1.1 selector is removed in some places

        given:
        def libUpdated = mavenRepo.module('org', 'lib', '1.1')
        // depend on newer version of lib (libUpdated) that is not published, the internal state is broken and deserialization expects 1.1 to exist
        def between = mavenRepo.module('org', 'between', '1.0').dependsOn(libUpdated).publish()
        mavenRepo.module('org', 'lib', '1.0').dependsOn(between).publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }

            dependencies {
                implementation 'org:lib:1.0'
            }
        """
        expect:
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'
    }

    def 'local cycle between dependencies does not causes a ConcurrentModificationException during selector removal'() {
        given:
        def lib2 = mavenRepo.module('org', 'lib', '2.0').publish()
        def lib3 = mavenRepo.module('org', 'lib', '3.0').publish()
        def lib1 = mavenRepo.module('org', 'lib', '1.0')
        // recursive dependencies between different versions of 'lib'
            .dependencyConstraint(lib3).dependencyConstraint(lib2).withModuleMetadata().publish()

        mavenRepo.module('org', 'direct', '1.0').dependsOn(lib1).publish()

        def directUpdated = mavenRepo.module('org', 'direct', '1.1').publish()
        def b = mavenRepo.module('org', 'b', '1.0').dependsOn(directUpdated).publish()
        mavenRepo.module('org', 'a', '1.0').dependsOn(b).publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }

            dependencies {
                implementation 'org:direct:1.0'  // dependeincy on 'lib'
                implementation 'org:a:1.0'       // updates direct (to remove dependency on 'lib' again)
            }
        """

        expect:
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'
    }

    def 'local cycle between dependencies does not causes a ConcurrentModificationException during selector removal with strict version endorsement'() {
        given:
        def direct11 = mavenRepo.module('org', 'direct', '1.1')
        def betweenLibAndDirect = mavenRepo.module('org', 'betweenLibAndDirect', '1.0').dependsOn(direct11)
        def lib2 = mavenRepo.module('org', 'lib', '2.0').publish()
        def lib1 = mavenRepo.module('org', 'lib', '1.0').dependsOn(betweenLibAndDirect)
        // recursive dependencies between different versions of 'lib'
            .dependencyConstraint(lib2).withModuleMetadata().publish()
        def lib05 = mavenRepo.module('org', 'lib', '0.5').publish()

        mavenRepo.module('org', 'direct', '1.0')
        // endorsing dependency that will cause a reselection of the parent again causing a reselection of other children
        // ("looping back" if a another child is the same as the one that was endorsed)
            .dependsOn([endorseStrictVersions: true], lib1).dependsOn(lib05).dependsOn(lib1).withModuleMetadata().publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }

            dependencies {
                implementation 'org:direct:1.0'  // dependency on 'lib' which will istself update 'direct'
            }
        """

        expect:
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'
    }

    def 'local cycle between dependencies does not causes a ConcurrentModificationException during selector removal with multiple strict version endorsements'() {
        given:
        def foo2 = mavenRepo.module('org', 'foo', '2.0').publish()
        def foo1 = mavenRepo.module('org', 'foo', '1.0')
            .dependencyConstraint(foo2).withModuleMetadata().publish()
        def foo05 = mavenRepo.module('org', 'foo', '0.5').publish()

        def direct11 = mavenRepo.module('org', 'direct', '1.1')
        def betweenLibAndDirect = mavenRepo.module('org', 'betweenLibAndDirect', '1.0').dependsOn(direct11)
        def lib2 = mavenRepo.module('org', 'lib', '2.0').publish()
        def lib1 = mavenRepo.module('org', 'lib', '1.0').dependsOn(betweenLibAndDirect)
        // recursive dependencies between different versions of 'lib'
            .dependencyConstraint(lib2).withModuleMetadata().publish()
        def lib05 = mavenRepo.module('org', 'lib', '0.5').publish()

        mavenRepo.module('org', 'direct', '1.0')
        // endorsing dependency that will cause a reselection of the parent again causing a reselection of other children
        // ("looping back" if a another child is the same as the one that was endorsed)
            .dependsOn([endorseStrictVersions: true], lib1)
            .dependsOn([endorseStrictVersions: true], foo1).dependsOn(lib05).dependsOn(lib1).dependsOn([endorseStrictVersions: true], foo05).withModuleMetadata().publish()

        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }

            dependencies {
                implementation 'org:direct:1.0'  // dependency on 'lib' which will istself update 'direct'
            }
        """

        expect:
        succeeds 'dependencies', '--configuration', 'runtimeClasspath'
    }

    def "can resolve a graph with an obvious version cycle by breaking the cycle"() {
        given:
        def direct2 = mavenRepo.module('org', 'direct', '2.0').publish()
        def trans = mavenRepo.module('org', 'transitive', '1.0').dependsOn(direct2).publish()
        mavenRepo.module('org', 'direct', '1.0').dependsOn(trans).publish()

        buildFile << """
repositories {
    maven {
        name = 'repo'
        url = "${mavenRepo.uri}"
    }
}

configurations {
    conf
}

dependencies {
    conf "org:direct:1.0"
}
"""
        expect:
        succeeds 'dependencies', '--configuration', 'conf'
    }

    def "can resolve a graph with a local cycle caused by module replacement"() {
        given:
        def child1 = mavenRepo.module('org', 'child1', '1.0').publish()
        def child2 = mavenRepo.module('org', 'child2', '1.0').publish()
        mavenRepo.module('org', 'direct', '1.0').dependsOn(child1).dependsOn(child2).publish()

        buildFile << """
            repositories {
                maven {
                    name = 'repo'
                    url = "${mavenRepo.uri}"
                }
            }

            configurations {
                conf
            }

            dependencies {
                modules {
                    module("org:child1") {
                        replacedBy("org:direct")
                    }
                }
                conf "org:direct:1.0"
            }
        """

        expect:
        succeeds 'dependencies', '--configuration', 'conf'
    }

    def 'does not cache node dependencies when node is deselected then reselected with different exclude filter'() {
        given:
        // Excluded module
        def excluded = mavenRepo.module('org.test', 'excluded', '1.0').publish()

        // Intermediates
        def intermediate10 = mavenRepo.module('org.test', 'intermediate1', '1.0').dependsOn(excluded).publish()
        def intermediate20 = mavenRepo.module('org.test', 'intermediate1', '2.0').dependsOn(excluded).publish()
        def intermediate2 = mavenRepo.module('org.test', 'intermediate2', '1.0').dependsOn(intermediate10).publish()
        def intermediate3 = mavenRepo.module('org.test', 'intermediate3', '1.0').dependsOn(intermediate2).publish()

        // Aligned modules
        def firstAligned = mavenRepo.module('org.aligned', 'aligned1', '1.0').dependsOn(intermediate20).publish()
        mavenRepo.module('org.aligned', 'aligned1', '2.0').dependsOn(intermediate20).publish()
        def otherAligned = mavenRepo.module('org.aligned', 'aligned2', '2.0').publish()

        // Roots
        mavenRepo.module('org.test', 'excludingRoot', '1.0').dependsOn(firstAligned, exclusions: [[group: 'org.test', module: 'excluded']]).publish()
        mavenRepo.module('org.test', 'root2', '1.0').dependsOn(intermediate3).publish()
        mavenRepo.module('org.test', 'root3', '1.0').dependsOn(otherAligned).publish()

        buildFile << """
            repositories {
                maven {
                    name = 'repo'
                    url = "${mavenRepo.uri}"
                }
            }

            configurations {
                conf
            }

            dependencies {
                conf 'org.test:excludingRoot:1.0'
                conf 'org.test:root2:1.0'
                conf 'org.test:root3:1.0'

                components.all(AlignGroup.class)
            }

            class AlignGroup implements ComponentMetadataRule {
                void execute(ComponentMetadataContext ctx) {
                    ctx.details.with { it ->
                        if (it.getId().getGroup().startsWith("org.aligned")) {
                            it.belongsTo("org.aligned:platform:\${it.getId().getVersion()}")
                        }
                    }
                }
            }
"""
        when:
        succeeds 'dependencies', '--configuration', 'conf'

        then:
        outputContains('excluded')
    }

}
