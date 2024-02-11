/*
 * Copyright 2013 the original author or authors.
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

class IvyModuleResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "wildcard on LHS of configuration mapping includes all public configurations of target module"() {
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
            .artifact()
            .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: 'runtime->*')
            .publish()

        ivyRepo.module('ivy.configuration', 'projectB', '1.5')
            .configuration('child')
            .configuration('private', visibility: 'private')
            .artifact()
            .artifact([name: 'projectB', conf: 'runtime'])
            .artifact([name: 'projectB-child', conf: 'child'])
            .artifact([name: 'projectB-private', conf: 'private'])
            .dependsOn(organisation: 'ivy.configuration', module: 'projectC', revision: '1.7', conf: 'child->*')
            .dependsOn(organisation: 'ivy.configuration', module: 'projectD', revision: 'broken', conf: 'private->*')
            .publish()

        ivyRepo.module('ivy.configuration', 'projectC', '1.7')
            .artifact()
            .publish()

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
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-other-1.6.jar', 'projectD-1.0.jar')
    }

    def "fails when project dependency references a configuration that does not exist"() {
        ivyRepo.module('test', 'target', '1.0').publish()

        buildFile << """
configurations {
    compile
}
repositories {
    ivy { url "${ivyRepo.uri}" }
}
dependencies {
    compile group: 'test', name: 'target', version: '1.0', configuration: 'x86_windows'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""

        expect:
        fails 'retrieve'
        failure.assertHasCause("Could not resolve test:target:1.0.\nRequired by:\n    project :")
        failure.assertHasCause("A dependency was declared on configuration 'x86_windows' which is not declared in the descriptor for test:target:1.0.")
    }

    def "fails when ivy module references a configuration that does not exist"() {
        def b = ivyRepo.module('test', 'b', '1.0').publish()
        ivyRepo.module('test', 'a', '1.0')
        ivyRepo.module('test', 'target', '1.0')
            .configuration('something')
            .dependsOn(b, conf: 'something->unknown')
            .publish()

        buildFile << """
configurations {
    compile
}
repositories {
    ivy { url "${ivyRepo.uri}" }
}
dependencies {
    compile group: 'test', name: 'target', version: '1.0', configuration: 'something'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""

        expect:
        fails 'retrieve'
        failure.assertHasCause("Test:target:1.0 declares a dependency from configuration 'something' to configuration 'unknown' which is not declared in the descriptor for test:b:1.0.")
    }

    def "correctly handles configuration mapping rule '#rule'"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy { url "${ivyHttpRepo.uri}" }
    }
    compile group: 'ivy.configuration', name: 'projectA', version: '1.2', configuration: 'a'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        def projectA = ivyHttpRepo.module('ivy.configuration', 'projectA', '1.2')
            .configuration("parent")
            .configuration("a", extendsFrom: ["parent"])
            .configuration("b")
            .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: rule)
            .publish()

        def projectB = ivyHttpRepo.module('ivy.configuration', 'projectB', '1.5')
            .configuration('a')
            .configuration('b')
            .configuration('c')
            .configuration('d', visibility: 'private')
            .artifact([name: 'projectB-a', conf: 'a'])
            .artifact([name: 'projectB-b', conf: 'b'])
            .artifact([name: 'projectB-c', conf: 'c'])
            .artifact([name: 'projectB-d', conf: 'd'])
            .dependsOn(organisation: 'ivy.configuration', module: 'projectC', revision: '1.7', conf: 'a->default')
            .dependsOn(organisation: 'ivy.configuration', module: 'projectD', revision: '1.7', conf: 'b->default')
            .dependsOn(organisation: 'ivy.configuration', module: 'projectE', revision: '1.7', conf: 'd->default')
            .publish()

        def projectC = ivyHttpRepo.module('ivy.configuration', 'projectC', '1.7').publish()
        def projectD = ivyHttpRepo.module('ivy.configuration', 'projectD', '1.7').publish()

        projectA.allowAll()
        projectB.allowAll()
        projectC.allowAll()
        projectD.allowAll()

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants(['projectA-1.2.jar'] + jars)

        when:
        server.resetExpectations()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants(['projectA-1.2.jar'] + jars)

        where:
        rule                    | jars
        "a"                     | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->b"                  | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "a,b->b"                | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "parent->a"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a,parent->a"           | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->a,b"                | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "a;a->b"                | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "*->a"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->*"                  | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectB-c-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "*->@"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a,b->@"                | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "runtime->unknown;%->@" | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->a;%->b"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*,!b->b"               | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "b"                     | []
        "*,!a->a"               | []
        "a->#"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "parent->#"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->#"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->unknown(a)"         | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->unknown(*)"         | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectB-c-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "a->a(*),b(*);b->b(*)"  | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
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
            resolve.dynamicMode = project.hasProperty('useDynamicResolve')
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

    def "prefers module with metadata to module with no metadata"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def moduleWithNoMetaData = repo1.module("org.gradle", "test", "1.45").withNoMetaData().publish()
        def repo2 = ivyHttpRepo("repo2")
        def moduleWithMetaData = repo2.module("org.gradle", "test", "1.45").publishWithChangedContent()
        assert moduleWithNoMetaData.jarFile.text != moduleWithMetaData.jarFile.text

        and:
        buildFile << """
repositories {
    ivy {
        url "${repo1.uri}"
        metadataSources {
            ivyDescriptor()
            artifact()
        }
    }
    ivy {
        url "${repo2.uri}"
        metadataSources {
            ivyDescriptor()
            artifact()
        }
    }
}
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task retrieve(type: Sync) {
    from configurations.compile
    into "libs"
}
"""

        when:
        moduleWithNoMetaData.ivy.expectGetMissing()
        moduleWithNoMetaData.jar.expectHead()
        moduleWithMetaData.ivy.expectGet()
        moduleWithMetaData.jar.expectGet()
        succeeds "retrieve"

        then:
        file("libs").assertHasDescendants("test-1.45.jar")
        file("libs/test-1.45.jar").assertIsCopyOf(moduleWithMetaData.jarFile)

        when:
        server.resetExpectations()
        succeeds "retrieve"

        then:
        file("libs").assertHasDescendants("test-1.45.jar")
        file("libs/test-1.45.jar").assertIsCopyOf(moduleWithMetaData.jarFile)
    }

    def "removes redundant configurations from resolution result"() {
        given:
        settingsFile << "rootProject.name = 'test'"

        def resolve = new ResolveTestFixture(buildFile, "compile")
        buildFile << """
    group 'org.test'
    version '1.0'
    configurations {
        compile
    }
    repositories {
        ivy { url "${ivyRepo.uri}" }
    }
    dependencies {
        compile group: 'ivy.configuration', name: 'projectA', version: '1.2', configuration: 'a'
    }
    task retrieve(type: Sync) {
      from configurations.compile
      into 'libs'
    }
    """
        resolve.prepare()

        ivyRepo.module('ivy.configuration', 'projectA', '1.2')
            .configuration("a")
            .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: "a->parent,a,b,c")
            .publish()

        ivyRepo.module('ivy.configuration', 'projectB', '1.5')
            .configuration("parent")
            .configuration('a', extendsFrom: ["parent"])
            .configuration('b', extendsFrom: ["parent"])
            .configuration('c', extendsFrom: ["b"])
            .publish()

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", "org.test:test:1.0") {
                module("ivy.configuration:projectA:1.2") {
                    configuration("a")
                    module("ivy.configuration:projectB:1.5") {
                        variant('a', ['org.gradle.status': 'integration']) // b, parent are redundant
                        variant('c', ['org.gradle.status': 'integration']) // b, parent are redundant
                    }
                }
            }
        }
    }
}
