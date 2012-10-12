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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.MavenFileRepository

class ProjectDependencyResolveIntegrationTest extends AbstractIntegrationSpec {
    public void "project dependency includes artifacts and transitive dependencies of default configuration in target project"() {
        given:
        repo.module("org.other", "externalA", 1.2).publish()
        repo.module("org.other", "externalB", 2.1).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$repo.uri' } }
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
    }
}
"""

        expect:
        succeeds "check"
    }

    public void "project dependency that specifies a target configuration includes artifacts and transitive dependencies of selected configuration"() {
        given:
        repo.module("org.other", "externalA", 1.2).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    repositories { maven { url '$repo.uri' } }
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
        repo.module("group", "externalA", 1.5).publish()

        and:
        file('settings.gradle') << "include 'a', 'b'"

        and:
        buildFile << """
allprojects {
    apply plugin: 'base'
    repositories { maven { url '${repo.uri}' } }
}

project(":a") {
    configurations { 'default' {} }
    dependencies { 'default' 'group:externalA:1.5' }
    task aJar(type: Jar) { }
    artifacts { 'default' aJar }
}

project(":b") {
    configurations { compile }
    dependencies { compile(project(':a')) { artifact { name = 'a'; type = 'jar' } } }
    task test {
        inputs.files configurations.compile
        doFirst {
            assert configurations.compile.files.collect { it.name } == ['a.jar', 'externalA-1.5.jar']
        }
    }
}
"""

        expect:
        succeeds 'test'
    }

    public void "non-transitive project dependency includes only the artifacts of the target configuration"() {
        given:
        repo.module("group", "externalA", 1.5).publish()

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
        ":b:copyZip" in  nonSkippedTasks 
        
        and:
        file("b/build/copied/a-1.0.zip").exists()
    }
    
    def getRepo() {
        return new MavenFileRepository(file('repo'))
    }
}
