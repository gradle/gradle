/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ProjectDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    public void "project dependency includes artifacts and transitive dependencies of default configuration in target project"() {
        given:
        mavenRepo.module("org.other", "externalA", 1.2).publish()
        mavenRepo.module("org.other", "externalB", 2.1).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
project(":a") {
    configurations {
        api
        'default' { extendsFrom api }
    }
    dependencies {
        api "org.other:externalA:1.2"
        'default' "org.other:externalB:2.1"
    }
    task jar(type: Jar) { baseName = 'a' }
    artifacts { api jar }
}
project(":b") {
    configurations {
        compile
    }
    dependencies {
        compile project(':a')
    }

    task check(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['a.jar', 'externalA-1.2.jar', 'externalB-2.1.jar']
        def result = configurations.compile.incoming.resolutionResult

         // Check root component
        def rootId = result.root.id
        assert rootId instanceof ModuleComponentIdentifier
        def rootPublishedAs = result.root.publishedAs
        assert rootPublishedAs instanceof ModuleComponentIdentifier
        assert rootPublishedAs.group == rootId.group
        assert rootPublishedAs.module == rootId.module
        assert rootPublishedAs.version == rootId.version

        // Check project components
        def projectDependencies = result.root.dependencies.selected.findAll { it.id instanceof BuildComponentIdentifier }
        assert projectDependencies.size() == 1
        def projectA = projectDependencies[0]
        assert projectA.id.projectPath == ':a'
        assert projectA.publishedAs instanceof ModuleComponentIdentifier
        assert projectA.publishedAs.group != null
        assert projectA.publishedAs.module == 'a'
        assert projectA.publishedAs.version == 'unspecified'

        // Check external module components
        def externalComponents = result.allDependencies.selected.findAll { it.id instanceof ModuleComponentIdentifier }
        assert externalComponents.size() == 2
        def externalA = externalComponents[0]
        assert externalA.id.group == 'org.other'
        assert externalA.id.module == 'externalA'
        assert externalA.id.version == '1.2'
        assert externalA.id == externalA.publishedAs
        def externalB = externalComponents[1]
        assert externalB.id.group == 'org.other'
        assert externalB.id.module == 'externalB'
        assert externalB.id.version == '2.1'
        assert externalB.id == externalB.publishedAs
    }
}
"""

        expect:
        succeeds "check"
    }

    public void "project dependency that specifies a target configuration includes artifacts and transitive dependencies of selected configuration"() {
        given:
        mavenRepo.module("org.other", "externalA", 1.2).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$mavenRepo.uri' } }
}
project(":a") {
    configurations {
        api
        runtime { extendsFrom api }
    }
    dependencies {
        api "org.other:externalA:1.2"
    }
    task jar(type: Jar) { baseName = 'a' }
    artifacts { api jar }
}
project(":b") {
    configurations {
        compile
    }
    dependencies {
        compile project(path: ':a', configuration: 'runtime')
    }
    task check(dependsOn: configurations.compile) << {
        assert configurations.compile.collect { it.name } == ['a.jar', 'externalA-1.2.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    @Issue("GRADLE-2899")
    public void "consuming project can refer to multiple configurations of target project"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
project(':a') {
    configurations {
        configA1
        configA2
    }
    task A1jar(type: Jar) {
        archiveName = 'A1.jar'
    }
    task A2jar(type: Jar) {
        archiveName = 'A2.jar'
    }
    artifacts {
        configA1 A1jar
        configA2 A2jar
    }
}

project(':b') {
    configurations {
        configB1
        configB2
    }
    dependencies {
        configB1 project(path:':a', configuration:'configA1')
        configB2 project(path:':a', configuration:'configA2')
    }
    task check << {
        assert configurations.configB1.collect { it.name } == ['A1.jar']
        assert configurations.configB2.collect { it.name } == ['A2.jar']
    }
}
"""

        expect:
        succeeds "check"
    }

    public void "resolved project artifacts contain project version in their names"() {
        given:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        file('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task aJar(type: Jar) { }
            artifacts { compile aJar }
'''
        file('b/build.gradle') << '''
            apply plugin: 'base'
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile bJar }
'''
        file('build.gradle') << '''
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile'), project(path: ':b', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assert configurations.compile.collect { it.name } == ['a.jar', 'b-late.jar']
            }
'''

        expect:
        succeeds "test"
    }

    public void "project dependency that references an artifact includes the matching artifact only plus the transitive dependencies of referenced configuration"() {
        given:
        mavenRepo.module("group", "externalA", 1.5).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    apply plugin: 'base'
    repositories { maven { url '${mavenRepo.uri}' } }
}

project(":a") {
    configurations { 'default' {} }
    dependencies { 'default' 'group:externalA:1.5' }
    task aJar(type: Jar) { baseName='a' }
    task bJar(type: Jar) { baseName='b' }
    artifacts { 'default' aJar, bJar }
}

project(":b") {
    configurations { compile }
    dependencies { compile(project(':a')) { artifact { name = 'b'; type = 'jar' } } }
    task test {
        inputs.files configurations.compile
        doFirst {
            assert configurations.compile.files.collect { it.name } == ['b.jar', 'externalA-1.5.jar']
        }
    }
}
"""

        expect:
        succeeds 'test'
    }

    public void "reports project dependency that refers to an unknown artifact"() {
        given:
        file('settings.gradle') << """
include 'a', 'b'
"""

        and:
        buildFile << """
allprojects { group = 'test' }
project(":a") {
    configurations { 'default' {} }
}

project(":b") {
    configurations { compile }
    dependencies { compile(project(':a')) { artifact { name = 'b'; type = 'jar' } } }
    task test {
        inputs.files configurations.compile
        doFirst {
            assert configurations.compile.files.collect { it.name } == ['a-b.jar', 'externalA-1.5.jar']
        }
    }
}
"""

        expect:
        fails 'test'

        and:
        failure.assertResolutionFailure(":b:compile").assertHasCause("Artifact 'test:a:unspecified:b.jar' not found.")
    }

    public void "non-transitive project dependency includes only the artifacts of the target configuration"() {
        given:
        mavenRepo.module("group", "externalA", 1.5).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << '''
allprojects {
    apply plugin: 'java'
    repositories { maven { url rootProject.uri('repo') } }
}
project(':a') {
    dependencies {
        compile 'group:externalA:1.5'
        compile files('libs/externalB.jar')
    }
}
project(':b') {
    dependencies {
        compile project(':a'), { transitive = false }
    }
    task listJars << {
        assert configurations.compile.collect { it.name } == ['a.jar']
    }
}
'''

        expect:
        succeeds "listJars"
    }

    public void "can have cycle in project dependencies"() {
        given:
        file('settings.gradle') << "include 'a', 'b', 'c'"

        and:
        buildFile << """

subprojects {
    apply plugin: 'base'
    configurations {
        'default'
        other
    }
    task jar(type: Jar)
    artifacts {
        'default' jar
    }
}

project('a') {
    dependencies {
        'default' project(':b')
        other project(':b')
    }
    task listJars {
        dependsOn configurations.default
        dependsOn configurations.other
        doFirst {
            def jars = configurations.default.collect { it.name } as Set
            assert jars == ['a.jar', 'b.jar', 'c.jar'] as Set

            jars = configurations.other.collect { it.name } as Set
            assert jars == ['a.jar', 'b.jar', 'c.jar'] as Set
        }
    }
}

project('b') {
    dependencies {
        'default' project(':c')
    }
}

project('c') {
    dependencies {
        'default' project(':a')
    }
}
"""

        expect:
        succeeds "listJars"
    }

    // this test is largely covered by other tests, but does ensure that there is nothing special about
    // project dependencies that are “built” by built in plugins like the Java plugin's created jars
    def "can use zip files as project dependencies"() {
        given:
        file("settings.gradle") << "include 'a'; include 'b'"
        file("a/some.txt") << "foo"
        file("a/build.gradle") << """
            group = "g"
            version = 1.0
            
            apply plugin: 'base'
            task zip(type: Zip) {
                from "some.txt"
            }

            artifacts {
                delegate.default zip
            }
        """
        file("b/build.gradle") << """
            configurations { conf }
            dependencies {
                conf project(":a")
            }
            
            task copyZip(type: Copy) {
                from configurations.conf
                into "\$buildDir/copied"
            }
        """
        
        when:
        succeeds ":b:copyZip"
        
        then:
        ":b:copyZip" in nonSkippedTasks
        
        and:
        file("b/build/copied/a-1.0.zip").exists()
    }
}
