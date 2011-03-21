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


package org.gradle.integtests.tooling

import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiHonorsProjectCustomizationsTest extends ToolingApiSpecification {

    def "should honour reconfigured project names"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}

project(':api') {
    eclipseProject { projectName = 'gradle-api' }
}

project(':impl') {
    eclipseProject { projectName = 'gradle-impl' }
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
        def projectDir = dist.testDir
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

    def "should honor eclipse configuration hooks"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipseProject {
    projectName = 'impl'
    whenConfigured {
        it.name = 'fancy-impl'
    }
}
'''

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject.name == 'fancy-impl'
    }

    def "should honor reconfigured source folders"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'

sourceSets {
    main { java { srcDir 'src' }}

    test { java { srcDir 'test' }}
}
'''
        //if we don't create the folders eclipse plugin will not build the classpath
        projectDir.create {
            src {}
            test {}
        }

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject.sourceDirectories[0].path == 'src'
        eclipseProject.sourceDirectories[1].path == 'test'
    }
}
