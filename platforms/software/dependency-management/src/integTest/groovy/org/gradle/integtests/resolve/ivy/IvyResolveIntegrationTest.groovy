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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class IvyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        settingsFile """
            rootProject.name = 'test'
        """
    }

    def "a dependency on an ivy module includes all artifacts and transitive dependencies of referenced configuration"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45")
            .dependsOn("org.gradle", "other", "preview-1")
            .artifact()
            .artifact(classifier: "classifier")
            .artifact(name: "test-extra")
            .publish()

        ivyRepo.module("org.gradle", "other", "preview-1").publish()

        and:

        buildFile << """
group = 'org.gradle'
version = '1.0'
repositories { ivy { url "${ivyRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}
"""
        resolve.prepare()

        when:
        succeeds "checkDeps"

        then:
        resolve.expectGraph {
            root(":", "org.gradle:test:1.0") {
                module("org.gradle:test:1.45") {
                    byConflictResolution("between versions 1.45 and 1.0")
                    artifact()
                    artifact(classifier: "classifier")
                    artifact(name: "test-extra")
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency includes only the artifacts of the default configuration"() {
        given:
        server.start()
        def dep = ivyHttpRepo.module("org.gradle", "test", "1.45")
            .configuration('archives')
            .configuration('default', extendsFrom: ['archives'])
            .configuration('source')
            .configuration('javadoc')
            .artifact(conf: 'archives')
            .artifact(classifier: 'source', conf: 'source')
            .artifact(classifier: 'javadoc', conf: 'javadoc')
            .publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyHttpRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}
"""
        resolve.prepare()

        expect:
        dep.ivy.expectGet()
        dep.getArtifact().expectGet()

        when:
        succeeds "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45")
            }
        }

        when:
        // Need to check twice to use the cached version too
        succeeds "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45")
            }
        }
    }

    def "dependency that references a classifier includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45")
            .dependsOn("org.gradle", "other", "preview-1")
            .artifact(classifier: "classifier")
            .artifact(name: "test-extra")
            .publish()
        ivyRepo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45:classifier"
}
"""
        resolve.prepare()

        expect:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    artifact(classifier: "classifier")
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "dependency that references a classifier can resolve module with no metadata when artifact metadata source is configured"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45").withNoMetaData().artifact(classifier: "classifier").publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "${ivyRepo.uri}"
        metadataSources {
            ivyDescriptor()
            artifact()
        }
    }
}
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45:classifier"
}
"""
        resolve.prepare()

        expect:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    artifact(classifier: "classifier")
                }
            }
        }
    }

    def "dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        def dep = ivyHttpRepo.module("org.gradle", "test", "1.45")
            .dependsOn("org.gradle", "other", "preview-1")
            .artifact(classifier: "classifier")
            .artifact(name: "test-extra")
            .publish()
        def module2 = ivyHttpRepo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyHttpRepo.uri}" } }
configurations { compile }
dependencies {
    compile ("org.gradle:test:1.45") {
        artifact {
            name = 'test-extra'
            type = 'jar'
        }
    }
}
"""
        resolve.prepare()

        when:
        dep.ivy.expectGet()
        dep.getArtifact(name: 'test-extra').expectGet()
        module2.ivy.expectGet()
        module2.jar.expectGet()

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    artifact(name: "test-extra")
                    module("org.gradle:other:preview-1")
                }
            }
        }
    }

    def "uses correct artifact name for module with no metadata where artifact name does not match module name"() {
        given:
        def dep = ivyHttpRepo.module("org.gradle", "test", "1.45")
            .withNoMetaData()
            .artifact(name: 'my-test-artifact')
            .publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
        metadataSources {
            ivyDescriptor()
            artifact()
        }
    }
}
configurations { compile }
dependencies {
    compile ("org.gradle:test:1.45") {
        artifact {
            name = 'my-test-artifact'
            extension = 'jar'
            type = 'jar'
        }
    }
}
"""
        resolve.prepare()

        when:
        dep.ivy.expectGetMissing()
        dep.getArtifact(name: 'my-test-artifact').expectHead()
        dep.getArtifact(name: 'my-test-artifact').expectGet()

        then:
        succeeds "checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    artifact(name: "my-test-artifact")
                }
            }
        }
    }

    def "transitive flag of referenced configuration affects its transitive dependencies only"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45")
            .dependsOn("org.gradle", "other", "preview-1")
            .nonTransitive('default')
            .publish()
        ivyRepo.module("org.gradle", "other", "preview-1").dependsOn("org.gradle", "other2", "7").publish()
        ivyRepo.module("org.gradle", "other2", "7").publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyRepo.uri}" } }
configurations {
    compile
    runtime.extendsFrom compile
}
dependencies {
    compile "org.gradle:test:1.45"
    runtime "org.gradle:other:preview-1"
}
"""
        resolve.prepare {
            config("compile")
            config("runtime")
        }

        when:
        succeeds "checkCompile"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle:other:preview-1") // no dependencies
                }
            }
        }

        when:
        succeeds "checkRuntime"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.gradle:test:1.45") {
                    module("org.gradle:other:preview-1") {
                        module("org.gradle:other2:7")
                    }
                }
                module("org.gradle:other:preview-1")
            }
        }
    }

    def "a constraint should not prevent the selection of an explicit Ivy configuration (both direct and transitive)"() {
        settingsFile << """
            rootProject.name = 'test'
        """
        def main = ivyRepo.module("org", "foo")
            .configuration("bob")
            .configuration("alice")
            .dependsOn(organisation: "org", module: "bar", revision: "1.0", conf: 'alice->extra')
        main.configurations.remove('default')
        main.publish()

        def dep = ivyRepo.module("org", "bar", "1.0")
            .configuration("extra")
        dep.configurations.remove("default")
        dep.publish()

        given:
        buildFile << """
            group = 'com.acme'
            version = '1.9'

            repositories { ivy { url "${ivyRepo.uri}" } }

            apply plugin: 'java-library'

            dependencies {
                constraints {
                    api("org:foo") {
                        version {
                            strictly '1.0'
                        }
                    }
                    api("org:bar") {
                        version {
                            strictly '1.0'
                        }
                    }
                }
                dependencies {
                    implementation(group:"org", name:"foo", configuration:"alice")
                }
            }
        """
        resolve.prepare("compileClasspath")

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", "com.acme:test:1.9") {
                edge('org:foo', 'org:foo:1.0') {
                    configuration('alice')
                    byConstraint()
                    module('org:bar:1.0') {
                        notRequested()
                        byAncestor()
                        byConstraint()
                        configuration('extra')
                    }
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:bar:{strictly 1.0}", "org:bar:1.0")
            }
        }
    }
}
