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
package org.gradle.integtests.tooling.m5

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject

@MinToolingApiVersion('1.0-milestone-5')
@MinTargetGradleVersion('1.0-milestone-5')
class ToolingApiHonorsProjectCustomizationsCrossVersionSpec extends ToolingApiSpecification {

    def "should honour reconfigured project names"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}

project(':api') {
    eclipse.project.name = 'gradle-api'
}

project(':impl') {
    eclipse.project.name = 'gradle-impl'
}
'''
        projectDir.file('settings.gradle').text = "include 'api', 'impl'"

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        EclipseProject api = eclipseProject.children[1]
        assert api.name == 'gradle-api'
        EclipseProject impl = eclipseProject.children[0]
        assert impl.name == 'gradle-impl'
    }

    def "should deduplicate project names"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
}
'''
        projectDir.file('settings.gradle').text = "include 'services:api', 'contrib:api'"

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        String grandChildOne = eclipseProject.children[0].children[0].name
        String grandChildTwo = eclipseProject.children[1].children[0].name
        assert grandChildOne != grandChildTwo : "Deduplication logic should make that project names are not the same."
    }

    def "can have overlapping source and resource directories"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main {
        java { srcDir 'src' }
        resources { srcDir 'src' }
    }

    test {
        java { srcDir 'test' }
        resources { srcDir 'testResources' }
    }
}
'''
        //if we don't create the folders eclipse plugin will not build the classpath
        projectDir.create {
            src {}
            test {}
            testResources {}
        }

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject.sourceDirectories.size() == 3
        eclipseProject.sourceDirectories[0].path == 'src'
        eclipseProject.sourceDirectories[1].path == 'testResources'
        eclipseProject.sourceDirectories[2].path == 'test'
    }

    def "can enable download of Javadoc for external dependencies"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'
repositories { mavenCentral() }
dependencies {
    compile 'commons-lang:commons-lang:2.5'
    runtime 'commons-io:commons-io:1.4'
}
eclipse { classpath { downloadJavadoc = true } }
'''

        when:
        EclipseProject eclipseProject = withConnection { ProjectConnection connection ->
            def builder = connection.model(EclipseProject.class)
            return builder.get()
        }

        then:
        eclipseProject.classpath.size() == 2
        eclipseProject.classpath.collect { it.file.name } as Set == ['commons-lang-2.5.jar', 'commons-io-1.4.jar' ] as Set
        eclipseProject.classpath.collect { it.source?.name } as Set == ['commons-lang-2.5-sources.jar', 'commons-io-1.4-sources.jar'] as Set
        eclipseProject.classpath.collect { it.javadoc?.name } as Set == ['commons-lang-2.5-javadoc.jar', 'commons-io-1.4-javadoc.jar'] as Set
    }
}
