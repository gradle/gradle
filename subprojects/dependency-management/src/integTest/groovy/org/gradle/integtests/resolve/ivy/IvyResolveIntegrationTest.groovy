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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class IvyResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
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
        settingsFile << """
rootProject.name = 'testproject'
"""

        buildFile << """
group = 'org.gradle'
version = '1.0'
repositories { ivy { url "${ivyRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'test-1.45-classifier.jar', 'test-extra-1.45.jar', 'other-preview-1.jar']
        def result = configurations.compile.incoming.resolutionResult

        // Check root component
        def rootId = result.root.id
        assert rootId instanceof ProjectComponentIdentifier
        def rootPublishedAs = result.root.moduleVersion
        assert rootPublishedAs.group == 'org.gradle'
        assert rootPublishedAs.name == 'testproject'
        assert rootPublishedAs.version == '1.0'

        // Check external module components
        def externalComponents = result.root.dependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }
        assert externalComponents.size() == 1
        def selectedExternalComponent = externalComponents[0]
        assert selectedExternalComponent.id.group == 'org.gradle'
        assert selectedExternalComponent.id.module == 'test'
        assert selectedExternalComponent.id.version == '1.45'
        assert selectedExternalComponent.moduleVersion.group == 'org.gradle'
        assert selectedExternalComponent.moduleVersion.name == 'test'
        assert selectedExternalComponent.moduleVersion.version == '1.45'

        // Check external dependencies
        def externalDependencies = result.root.dependencies.requested.findAll { it instanceof ModuleComponentSelector }
        assert externalDependencies.size() == 1
        def requestedExternalDependency = externalDependencies[0]
        assert requestedExternalDependency.group == 'org.gradle'
        assert requestedExternalDependency.module == 'test'
        assert requestedExternalDependency.version == '1.45'
    }
}
"""

        expect:
        succeeds "check"
    }

    @ToBeFixedForInstantExecution
    def "dependency includes only the artifacts of the default configuration"() {
        given:
        server.start()
        def module = ivyHttpRepo.module("org.gradle", "test", "1.45")
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

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45.jar']
    }
}
"""

        expect:
        module.ivy.expectGet()
        module.getArtifact().expectGet()
        succeeds "check"

        // Need to check twice to use the cached version too
        succeeds "check"
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

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar', 'other-preview-1.jar']
    }
}
"""

        expect:
        succeeds "check"
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

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        def module = ivyHttpRepo.module("org.gradle", "test", "1.45")
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

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['test-extra-1.45.jar', 'other-preview-1.jar']
    }
}
"""

        when:
        module.ivy.expectGet()
        module.getArtifact(name: 'test-extra').expectGet()
        module2.ivy.expectGet()
        module2.jar.expectGet()

        then:
        succeeds "check"
    }

    def "uses correct artifact name for module with no metadata where artifact name does not match module name"() {
        given:
        def module = ivyHttpRepo.module("org.gradle", "test", "1.45")
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

task check {
    doLast {
        assert configurations.compile.collect { it.name } == ['my-test-artifact-1.45.jar']
    }
}
"""

        when:
        module.ivy.expectGetMissing()
        module.getArtifact(name: 'my-test-artifact').expectHead()
        module.getArtifact(name: 'my-test-artifact').expectGet()

        then:
        succeeds "check"
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

task check {
    doLast {
        def spec = { it.name == 'test' } as Spec

        assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
        assert configurations.compile.resolvedConfiguration.getFiles(spec).collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']

        assert configurations.runtime.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar', 'other2-7.jar']
        assert configurations.compile.resolvedConfiguration.getFiles(spec).collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    def "a constraint should not prevent the selection of an explicit Ivy configuration (both direct and transitive)"() {
        settingsFile << """
            rootProject.name = 'test'
        """
        def main = ivyRepo.module("org", "foo")
            .configuration("bob")
            .configuration("alice")
            .dependsOn(organisation:"org", module:"bar", revision:"1.0", conf: 'alice->extra')
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
        def resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", "com.acme:test:1.9") {
                edge('org:foo', 'org:foo:1.0') {
                    configuration('alice')
                    byConstraint()
                    module('org:bar:1.0') {
                        byAncestor()
                        byConstraint()
                        configuration('extra')
                    }
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
                constraint("org:bar:{strictly 1.0}","org:bar:1.0")
            }
        }
    }
}
