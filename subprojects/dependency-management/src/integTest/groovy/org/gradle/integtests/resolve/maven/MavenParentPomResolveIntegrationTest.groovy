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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class MavenParentPomResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "includes dependencies from parent pom"() {
        given:
        def parentDep = mavenHttpRepo.module("org", "parent_dep", "1.2").publish()
        def childDep = mavenHttpRepo.module("org", "child_dep", "1.7").publish()

        def parent = mavenHttpRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.dependsOn("org", "parent_dep", "1.2")
        parent.publish()

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.dependsOn("org", "child_dep", "1.7")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        parent.pom.expectGet()

        child.artifact.expectGet()

        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()
        childDep.pom.expectGet()
        childDep.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar', 'parent_dep-1.2.jar', 'child_dep-1.7.jar')

        when:
        server.resetExpectations()
        file('libs').deleteDir()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar', 'parent_dep-1.2.jar', 'child_dep-1.7.jar')
    }

    @ToBeFixedForConfigurationCache
    def "uses dependencyManagement from parent pom"() {
        given:
        mavenRepo.module("org", "child_dep", "1.7").publish()
        mavenRepo.module("org", "typed_dep", "1.8").artifact(type: 'bar').publish()
        mavenRepo.module("org", "classified_dep", "1.9").artifact(classifier: 'classy').publish()
        mavenRepo.module("org", "fq_dep", "2.1").artifact(type: 'bar', classifier: 'classy').publish()

        def parent = mavenRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.dependencyConstraint(mavenRepo.module('org', 'child_dep', '1.7'))
        parent.dependencyConstraint(mavenRepo.module('org', 'child_dep', '44'), type: 'bar')
        parent.dependencyConstraint(mavenRepo.module('org', 'child_dep', '44'), classifier: 'classy')

        parent.dependencyConstraint(mavenRepo.module('org', 'typed_dep', '44'))
        parent.dependencyConstraint(mavenRepo.module('org', 'typed_dep', '1.8'), type: 'bar')
        parent.dependencyConstraint(mavenRepo.module('org', 'typed_dep', '44'), type: 'zip')

        parent.dependencyConstraint(mavenRepo.module('org', 'classified_dep', '44'))
        parent.dependencyConstraint(mavenRepo.module('org', 'classified_dep', '1.9'), classifier: 'classy')
        parent.dependencyConstraint(mavenRepo.module('org', 'classified_dep', '44'), classifier: 'other')

        parent.dependencyConstraint(mavenRepo.module('org', 'fq_dep', '44'))
        parent.dependencyConstraint(mavenRepo.module('org', 'fq_dep', '44'), classifier: 'classy')
        parent.dependencyConstraint(mavenRepo.module('org', 'fq_dep', '44'), type: 'bar')
        parent.dependencyConstraint(mavenRepo.module('org', 'fq_dep', '2.1'), classifier: 'classy', type: 'bar')

        parent.publish()

        def child = mavenRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0")
        child.dependsOn("org", "child_dep", null)
        child.dependsOn("org", "typed_dep", null, "bar", null, null)
        child.dependsOn("org", "classified_dep", null, null, null, "classy")
        child.dependsOn("org", "fq_dep", null, "bar", null, "classy")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task libs { doLast { assert configurations.compile.files*.name == ['child-1.0.jar', 'child_dep-1.7.jar', 'typed_dep-1.8.bar', 'classified_dep-1.9-classy.jar', 'fq_dep-2.1-classy.bar'] } }
"""

        expect:
        succeeds 'libs'
        succeeds 'libs'
    }

    @Issue("GRADLE-2641")
    def "can handle parent pom with SNAPSHOT version"() {
        given:
        def parent = mavenHttpRepo.module("org", "parent", "1.0-SNAPSHOT")
        parent.hasPackaging('pom')
        parent.publish()

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0-SNAPSHOT")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        parent.metaData.expectGet()
        parent.pom.expectGet()

        child.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }

    def "looks for parent pom in different repository"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        def parentInRepo1 = repo1.module("org", "parent")

        def parentInRepo2 = repo2.module("org", "parent")
        parentInRepo2.hasPackaging('pom')
        parentInRepo2.publish()

        def child = repo1.module("org", "child")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${repo1.uri}' }
    maven { url '${repo2.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        child.artifact.expectGet()

        parentInRepo1.pom.expectGetMissing()

        parentInRepo2.pom.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }

    def "fails with reasonable message if parent module is an ivy module"() {
        given:
        def child = mavenHttpRepo.module("org", "child")
        child.parent("org", "parent", "1.0")
        child.publish()

        def missingChild = ivyHttpRepo.module("org", "child")
        def parent = ivyHttpRepo.module("org", "parent").publish()

        buildFile << """
repositories {
    ivy { url '${ivyHttpRepo.uri}' }
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies { compile 'org:child:1.0' }
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        missingChild.ivy.expectGetMissing()
        child.pom.expectGet()
        parent.ivy.expectGet()

        and:
        fails 'retrieve'

        then:
        failure.assertHasCause "Could not resolve org:child:1.0."
        failure.assertHasCause "Could not determine artifacts for org:parent:1.0: Cannot locate 'maven pom' artifacts for 'org:parent:1.0' in repository 'ivy'"
    }

    def "uses cached parent pom located in a different repository"() {
        given:
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        // Parent not found in repo1
        def missingParent = repo1.module("org", "parent")
        def parent = repo2.module("org", "parent", "1.0")
        parent.dependsOn("org", "parent_dep", "1.2")
                .hasPackaging('pom')
                .publish()

        def parentDep = repo1.module("org", "parent_dep", "1.2").publish()

        def child1 = repo1.module("org", "child1", "1.0")
        child1.parent("org", "parent", "1.0").publish()
        def child2 = repo1.module("org", "child2", "1.0")
        child2.parent("org", "parent", "1.0").publish()

        buildFile << """
repositories {
    maven { url '${repo1.uri}' }
    maven { url '${repo2.uri}' }
}
configurations {
    child1
    child2
}
dependencies {
    child1 'org:child1:1.0'
    child2 'org:child2:1.0'
}
task retrieveChild1(type: Sync) {
    into 'libs/child1'
    from configurations.child1
}
task retrieveChild2(type: Sync) {
    into 'libs/child2'
    from configurations.child2
}
"""

        when:
        child1.pom.expectGet()
        missingParent.pom.expectGetMissing()
        parent.pom.expectGet()

        child1.artifact.expectGet()

        parentDep.pom.expectGet()
        parentDep.artifact.expectGet()


        and:
        run 'retrieveChild1'

        then:
        file('libs/child1').assertHasDescendants('child1-1.0.jar', 'parent_dep-1.2.jar')

        when:
        server.resetExpectations()
        child2.pom.expectGet()
        child2.artifact.expectGet()

        and:
        run 'retrieveChild2'

        then:
        file('libs/child2').assertHasDescendants('child2-1.0.jar', 'parent_dep-1.2.jar')
    }

    @Issue("GRADLE-2861")
    def "two modules that share common parent"() {
        given:
        def parent = mavenHttpRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.publish()

        def child1 = mavenHttpRepo.module("org", "child1", "1.0")
        child1.parent("org", "parent", "1.0")
        child1.publish()
        def child2 = mavenHttpRepo.module("org", "child2", "1.0")
        child2.parent("org", "parent", "1.0")
        child2.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies {
    compile 'org:child1:1.0'
    compile 'org:child2:1.0'
}
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child1.pom.expectGet()
        child2.pom.expectGet()
        parent.pom.expectGet()

        child1.artifact.expectGet()
        child2.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child1-1.0.jar', 'child2-1.0.jar')
    }

    @Issue("GRADLE-2861")
    def "module that has a parent and grandparent module"() {
        given:
        def grandParent = mavenHttpRepo.module("org", "grandparent", "1.0")
        grandParent.hasPackaging('pom')
        grandParent.publish()

        def parent = mavenHttpRepo.module("org", "parent", "1.0")
        parent.hasPackaging('pom')
        parent.parent("org", "grandparent", "1.0")
        parent.publish()

        def child = mavenHttpRepo.module("org", "child", "1.0")
        child.parent("org", "parent", "1.0")
        child.publish()

        buildFile << """
repositories {
    maven { url '${mavenHttpRepo.uri}' }
}
configurations { compile }
dependencies {
    compile 'org:child:1.0'
}
task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        child.pom.expectGet()
        parent.pom.expectGet()
        grandParent.pom.expectGet()

        child.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('child-1.0.jar')
    }

    @ToBeFixedForConfigurationCache
    def "parent pom parsing with custom properties for dependency coordinates"() {
        given:
        def parent = mavenHttpRepo.module('group', 'parent', '1.0').publish()
        parent.pomFile.text = parent.pomFile.text.replace("</project>", """
<properties>
    <some.group>my.group</some.group>
    <some.artifact>myartifact</some.artifact>
    <some.version>1.1</some.version>
</properties>
<dependencies>
    <dependency>
        <groupId>\${some.group}</groupId>
        <artifactId>\${some.artifact}</artifactId>
        <version>\${some.version}</version>
    </dependency>
</dependencies>
</project>
""")

        def parentDepModule = mavenHttpRepo.module('my.group', 'myartifact', '1.1').publish()
        def module = mavenHttpRepo.module('group', 'artifact', '1.0').parent('group', 'parent', '1.0').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
            task libs { doLast { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar', 'myartifact-1.1.jar'] } }
        """

        and:
        parent.pom.expectGet()
        module.pom.expectGet()
        module.artifact.expectGet()
        parentDepModule.pom.expectGet()
        parentDepModule.artifact.expectGet()

        expect:
        // have to run twice to trigger the failure, to parse the descriptor from the cache
        succeeds ":libs"
        succeeds ":libs"
    }

    @ToBeFixedForConfigurationCache
    def "dependency with same group ID and artifact ID defined in child and parent is used from child"() {
        given:
        def parent = mavenHttpRepo.module('group', 'parent', '1.0').dependsOn('my.group', 'myartifact', '1.1').publish()
        mavenHttpRepo.module('my.group', 'myartifact', '1.1').publish()
        def depModule = mavenHttpRepo.module('my.group', 'myartifact', '1.3').publish()
        def module = mavenHttpRepo.module('group', 'artifact', '1.0').parent('group', 'parent', '1.0').dependsOn('my.group', 'myartifact', '1.3').publish()

        and:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }
            configurations { compile }
            dependencies {
                compile "group:artifact:1.0"
            }
            task libs { doLast { assert configurations.compile.files.collect {it.name} == ['artifact-1.0.jar', 'myartifact-1.3.jar'] } }
        """

        and:
        parent.pom.expectGet()
        module.pom.expectGet()
        module.artifact.expectGet()
        depModule.pom.expectGet()
        depModule.artifact.expectGet()

        expect:
        // have to run twice to trigger the failure, to parse the descriptor from the cache
        succeeds ":libs"
        succeeds ":libs"
    }
}
