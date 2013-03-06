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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class IvyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "dependency includes all artifacts and transitive dependencies of referenced configuration"() {
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

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45-classifier.jar', 'other-preview-1.jar']
}
"""

        expect:
        succeeds "check"
    }

    def "dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
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

    def "correctly handles wildcard configuration mapping in transitive dependencies"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy { url "${ivyRepo.uri}" }
    }
    compile 'ivy.configuration:projectA:1.2'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        when: "projectA uses a wildcard configuration mapping for dependency on projectB"
        def moduleA = ivyRepo.module('ivy.configuration', 'projectA', '1.2')
                .configuration('parent')
                .artifact([:])
                .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: 'runtime->*')
                .publish()

        ivyRepo.module('ivy.configuration', 'projectB', '1.5')
                .configuration('child')
                .artifact([name: 'projectB', conf: 'runtime'])
                .artifact([name: 'projectB-child', conf: 'child'])
                .dependsOn(organisation: 'ivy.configuration', module: 'projectC', revision: '1.7', conf: 'child->*')
                .publish()

        ivyRepo.module('ivy.configuration', 'projectC', '1.7').artifact([:]).publish()

        and:
        succeeds 'retrieve'

        then: "artifacts and dependencies from all configurations of projectB are included"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar', 'projectB-child-1.5.jar', 'projectC-1.7.jar')

        when: "projectB-1.5 is replaced by conflict resolution with projectB-1.6 that has a different set of configurations"

        ivyRepo.module('ivy.configuration', 'projectB', '1.6')
                .configuration('other')
                .artifact([name: 'projectB-other', conf: 'other'])
                .publish()

        ivyRepo.module('ivy.configuration', 'projectD', '1.0')
                .dependsOn('ivy.configuration', 'projectB', '1.6')
                .publish()

        moduleA.dependsOn('ivy.configuration', 'projectD', '1.0').publish()

        and:
        succeeds 'retrieve'

        then: "we resolve artifacts from projectB-1.6 only"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar', 'projectB-other-1.6.jar', 'projectD-1.0.jar')
    }

    def "prefers revConstraint over rev when dynamic resolve mode is used"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            resolve.dynamicResolve = project.hasProperty('useDynamicResolve')
        }
    }
    compile 'org:projectA:1.2'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        ivyRepo.module('org', 'projectA', '1.2')
                .dependsOn(organisation: 'org', module: 'projectB', revision: '1.5', revConstraint: '1.6')
                .dependsOn(organisation: 'org', module: 'projectC', revision: 'alpha-12')
                .publish()

        ivyRepo.module('org', 'projectB', '1.5')
                .publish()

        ivyRepo.module('org', 'projectB', '1.6')
                .publish()

        ivyRepo.module('org', 'projectC', 'alpha-12')
                .publish()

        when:
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar', 'projectC-alpha-12.jar')

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar', 'projectC-alpha-12.jar')
    }
}
