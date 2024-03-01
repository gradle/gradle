/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class EclipseLinkedResourceIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "can reference linked resources as source folders"() {
        given:
        multiProjectWithSiblingSourceFolders()
        when:
        run("eclipse")
        then:

        classpath("projectA").sources[0] == "src"
        classpath("projectA").sources[1] == "projectB-src"
        classpath("projectA").sources[2] == "sibling-source"
        classpath("projectA").sources[3] == "source-c"
        classpath("projectA").sources[4] == testDirectory.getName() + "-src"

        and:
        project("projectA").assertHasLinkedResources("projectB-src", "sibling-source", "source-c", "${testDirectory.name}-src")
    }

    def multiProjectWithSiblingSourceFolders() {
        settingsFile.text = """
rootProject.name = 'multiprojectroot'
include 'projectA'
include 'projectB'
include 'projectC'

"""
        buildFile.text = """
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}

configure(project(":projectA")){
    sourceSets {
        main {
            java {
                srcDirs = ['src', '../projectB/src', '../projectB/sibling-source', '../projectC/source-c', '../src']
            }
        }
    }
}
"""
        file("projectA/src").mkdirs()
        file("projectB/src").mkdirs()
        file("projectB/sibling-source").mkdirs()
        file("projectC/source-c").mkdirs()
        file("src").mkdirs()
    }

    def "can use linked resources and generate metadata twice"() {
        given:
        settingsFile.text = 'rootProject.name = "root"'
        buildScript '''
            plugins {
                id 'eclipse'
            }
            eclipse {
                project {
                    linkedResource name: 'README.md', type: '1', locationUri: 'PARENT-1-PROJECT_LOC/README.md'
                }
            }
        '''.stripIndent()

        when:
        run 'eclipse'

        then:
        project.assertHasLinkedResource('README.md', '1', 'PARENT-1-PROJECT_LOC/README.md')
        project.assertHasLinkedResources('README.md')

        and:
        run 'eclipse'

        then:
        project.assertHasLinkedResource('README.md', '1', 'PARENT-1-PROJECT_LOC/README.md')
        project.assertHasLinkedResources('README.md')
    }
}
