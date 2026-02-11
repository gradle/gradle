/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.consistency

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class JavaProjectResolutionConsistencyIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            apply plugin: 'java-library' // not using plugins { ... } because of the injected test fixture

            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                }
            }
        """
    }

    def "can configure the runtime classpath to be consistent with the compile classpath"() {
        withCompileClasspathAsReference()
        def foo = mavenHttpRepo.module('org', 'foo', '1.0')
            .dependsOn("org", "transitive", "1.0")
            .publish()
        def bar = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn("org", "transitive", "1.1")
            .publish()
        def baz = mavenHttpRepo.module('org', 'baz', '1.0')
            .dependsOn("org", "transitive", "1.2")
            .publish()
        def transitive10 = mavenHttpRepo.module('org', 'transitive', '1.0').publish()
        def transitive11 = mavenHttpRepo.module('org', 'transitive', '1.1').publish()
        buildFile << """
            dependencies {
                implementation 'org:foo:1.0'
                runtimeOnly 'org:bar:1.0'
                testImplementation 'org:baz:1.0'
            }

            ${resolve.configureProject("runtimeClasspath", "testRuntimeClasspath")}
        """

        when:
        foo.pom.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        bar.artifact.expectGet()
        transitive10.pom.expectGet()
        transitive10.artifact.expectGet()

        succeeds ':checkRuntimeClasspath'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution('compileClasspath')
                    module("org:transitive:1.0") {
                        notRequested()
                        byConsistentResolution('compileClasspath')
                        byAncestor()
                    }
                }
                module('org:bar:1.0') {
                    edge("org:transitive:1.1", "org:transitive:1.0")
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:transitive:{strictly 1.0}", "org:transitive:1.0")
            }
        }

        when:
        baz.pom.expectGet()
        baz.artifact.expectGet()
        succeeds ':checkTestRuntimeClasspath'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution('testCompileClasspath')
                    module("org:transitive:1.0")
                }
                module('org:bar:1.0') {
                    edge("org:transitive:1.1", "org:transitive:1.0") {
                        notRequested()
                        byConsistentResolution('testCompileClasspath')
                        byAncestor()
                    }
                }
                module('org:baz:1.0') {
                    byConsistentResolution('testCompileClasspath')
                    edge("org:transitive:1.2", "org:transitive:1.0") {
                        byConsistentResolution('testCompileClasspath')
                    }
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:baz:{strictly 1.0}", "org:baz:1.0")
                constraint("org:transitive:{strictly 1.0}", "org:transitive:1.0")
            }
        }

    }

    def "can configure the compile classpath to be consistent with the runtime classpath"() {
        withRuntimeClasspathAsReference()
        def foo = mavenHttpRepo.module('org', 'foo', '1.0')
            .dependsOn("org", "transitive", "1.0")
            .publish()
        def bar = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn("org", "transitive", "1.1")
            .publish()
        def transitive10 = mavenHttpRepo.module('org', 'transitive', '1.0').publish()
        def transitive11 = mavenHttpRepo.module('org', 'transitive', '1.1').publish()
        buildFile << """
            dependencies {
                implementation 'org:foo:1.0'
                runtimeOnly 'org:bar:1.0'
            }
            ${resolve.configureProject("compileClasspath")}
        """

        when:
        foo.pom.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        transitive10.pom.expectGet()
        transitive11.pom.expectGet()
        transitive11.artifact.expectGet()

        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution('runtimeClasspath')
                    edge("org:transitive:1.0", "org:transitive:1.1") {
                        notRequested()
                        byAncestor()
                        byConsistentResolution('runtimeClasspath')
                    }
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:transitive:{strictly 1.1}", "org:transitive:1.1")
            }
        }
    }

    def "custom source sets also benefit from configured consistent resolution"() {
        withCompileClasspathAsReference()
        def foo = mavenHttpRepo.module('org', 'foo', '1.0')
            .dependsOn("org", "transitive", "1.0")
            .publish()
        def bar = mavenHttpRepo.module('org', 'bar', '1.0')
            .dependsOn("org", "transitive", "1.1")
            .publish()
        def transitive10 = mavenHttpRepo.module('org', 'transitive', '1.0').publish()
        def transitive11 = mavenHttpRepo.module('org', 'transitive', '1.1').publish()
        buildFile << """
            sourceSets {
                custom
            }
            dependencies {
                customImplementation 'org:foo:1.0'
                customRuntimeOnly 'org:bar:1.0'
            }
            ${resolve.configureProject("customRuntimeClasspath")}
        """

        when:
        foo.pom.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()
        bar.artifact.expectGet()
        transitive10.pom.expectGet()
        transitive10.artifact.expectGet()

        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution('customCompileClasspath')
                    module("org:transitive:1.0") {
                        notRequested()
                        byConsistentResolution('customCompileClasspath')
                        byAncestor()
                    }
                }
                module('org:bar:1.0') {
                    edge("org:transitive:1.1", "org:transitive:1.0")
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:transitive:{strictly 1.0}", "org:transitive:1.0")
            }
        }
    }

    /**
     * This test relies on resolution at configuration time - THIS BEHAVIOR SHOULD NOT BE DUPLICATED, in
     * general, build authors should not force configurations to be resolved at configuration time.
     * <p>
     * For more details, see https://github.com/gradle/gradle/pull/36641.
     */
    def "can declare a configuration which extends from a resolvable configuration which uses consistency"() {
        withRuntimeClasspathAsReference()
        def foo = mavenHttpRepo.module('org', 'foo', '1.0').publish()
        def bar = mavenHttpRepo.module('org', 'bar', '1.0').publish()
        buildFile << """
            dependencies {
                implementation 'org:foo:1.0'
                runtimeOnly 'org:bar:1.0'
            }

            configurations {
                myCompileClasspath.extendsFrom(compileClasspath)
            }

            ${resolve.configureProject("compileClasspath")}
        """

        buildFile << """
            configurations.all {
                incoming.beforeResolve {
                    println("Resolving configuration: " + name)
                }
            }

            tasks.named('checkDeps') {
                def myCompileClasspathDeps = configurations.myCompileClasspath
                inputs.files myCompileClasspathDeps

                // in order to trigger the bug, we need to resolve the configuration
                // which extends from a resolvable configuration first
                configurations.myCompileClasspath.resolve()

                doFirst {
                    println "myCompileClasspath dependencies: " + myCompileClasspathDeps.files
                }
            }
        """

        when:
        foo.pom.expectGet()
        foo.artifact.expectGet()
        bar.pom.expectGet()

        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                module('org:foo:1.0') {
                    byConsistentResolution("runtimeClasspath")
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
            }
        }

        and: "the configuration which extends from the resolvable configuration is resolved first"
        output.indexOf("Resolving configuration: myCompileClasspath") < output.indexOf("Resolving configuration: compileClasspath")
    }


    private void configureConsistentResolution(String whatFirst) {
        buildFile << """
            java {
                consistentResolution {
                    use${whatFirst.capitalize()}ClasspathVersions()
                }
            }
        """
    }

    private void withCompileClasspathAsReference() {
        configureConsistentResolution 'compile'
    }

    private void withRuntimeClasspathAsReference() {
        configureConsistentResolution 'runtime'
    }
}
