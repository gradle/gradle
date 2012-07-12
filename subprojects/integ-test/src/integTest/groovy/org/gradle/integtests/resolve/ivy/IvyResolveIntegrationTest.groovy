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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class IvyResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "dependency includes all artifacts and transitive dependencies of referenced configuration"() {
        given:
        def repo = ivyRepo()
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: "classifier")
        module.artifact(name: "test-extra")
        module.publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { ivy { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'test-1.45-classifier.jar', 'test-extra-1.45.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references a classifier includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        def repo = ivyRepo()
        repo.module("org.gradle", "test", "1.45")
                .dependsOn("org.gradle", "other", "preview-1")
                .artifact(classifier: "classifier")
                .artifact(name: "test-extra")
                .publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { ivy { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45:classifier"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        def repo = ivyRepo()
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.artifact(classifier: "classifier")
        module.artifact(name: "test-extra")
        module.publish()
        repo.module("org.gradle", "other", "preview-1").publish()

        and:
        buildFile << """
repositories { ivy { url "${repo.uri}" } }
configurations { compile }
dependencies {
    compile ("org.gradle:test:1.45") {
        artifact {
            name = 'test-extra'
            type = 'jar'
        }
    }
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-extra-1.45.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "transitive flag of referenced configuration affects its transitive dependencies only"() {
        given:
        def repo = ivyRepo()
        def module = repo.module("org.gradle", "test", "1.45")
        module.dependsOn("org.gradle", "other", "preview-1")
        module.nonTransitive('default')
        module.publish()
        repo.module("org.gradle", "other", "preview-1").dependsOn("org.gradle", "other2", "7").publish()
        repo.module("org.gradle", "other2", "7").publish()

        and:
        buildFile << """
repositories { ivy { url "${repo.uri}" } }
configurations {
    compile
    runtime.extendsFrom compile
}
dependencies {
    compile "org.gradle:test:1.45"
    runtime "org.gradle:other:preview-1"
}

task check << {
    def spec = { it.name == 'test' } as Spec

    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
    assert configurations.compile.resolvedConfiguration.getFiles(spec).collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']

    assert configurations.runtime.collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar', 'other2-7.jar']
    assert configurations.compile.resolvedConfiguration.getFiles(spec).collect { it.name } == ['test-1.45.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }
}
