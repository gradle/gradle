/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r68

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.plugins.ide.tooling.r31.IdeaProjectUtil
import org.gradle.plugins.ide.tooling.r33.FetchEclipseProjects

@ToolingApiVersion('>=3.3')
@TargetGradleVersion(">=6.8")
class CompositeBuildModuleNamesCrossVersionSpec extends ToolingApiSpecification {

    def "name conflict between subproject and included build does not break IDEA import"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            include 'module-b'
            includeBuild('subfolder/module-b') { name = 'included-module-b' }
        """
        file('module-b').mkdir()
        file('subfolder/module-b').mkdirs()

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-main', 'module-b']

        allProjects.rootIdeaProject.name == 'module-main'
        allProjects.rootIdeaProject.modules.collect { it.name } == ['module-main', 'module-main-module-b']

        allProjects.getIdeaProject('module-main').modules.collect { it.name } == ['module-main', 'module-main-module-b']
        allProjects.getIdeaProject('module-b').modules.collect { it.name } == ['module-main-included-module-b']
        allProjects.getIdeaProject('module-b').modules.first().contentRoots.first().rootDirectory.parentFile.name == 'subfolder'
    }

    def "name conflict between subproject and included build does not break Eclipse import"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            include 'module-b'
            includeBuild('subfolder/module-b') { name = 'included-module-b' }
        """
        file('module-b').mkdir()
        file('subfolder/module-b').mkdirs()

        when:
        def eclipseProjects = withConnection { action(new FetchEclipseProjects()).run() }

        then:
        eclipseProjects.collect { it.name } == ['module-main', 'module-main-module-b', 'module-main-included-module-b']
    }

    def "name conflict between two included builds does not break IDEA import"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            includeBuild('subfolder1/module-b') { name = 'module-b-1' }
            includeBuild('subfolder2/module-b') { name = 'module-b-2' }
        """
        file('subfolder1/module-b').mkdirs()
        file('subfolder2/module-b').mkdirs()

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }
        def includedBuildProjects = allProjects.allIdeaProjects.findAll { it.name == 'module-b'}

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-main', 'module-b', 'module-b']

        allProjects.rootIdeaProject.name == 'module-main'
        allProjects.rootIdeaProject.modules.collect { it.name } == ['module-main']

        allProjects.getIdeaProject('module-main').modules.collect { it.name } == ['module-main']
        includedBuildProjects[0].modules.collect { it.name } == ['module-main-module-b-1']
        includedBuildProjects[1].modules.collect { it.name } == ['module-main-module-b-2']
        includedBuildProjects[0].modules.first().contentRoots.first().rootDirectory.parentFile.name == 'subfolder1'
        includedBuildProjects[1].modules.first().contentRoots.first().rootDirectory.parentFile.name == 'subfolder2'
    }

    def "name conflict between two included builds does not break Eclipse import"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            includeBuild('subfolder1/module-b') { name = 'module-b-1' }
            includeBuild('subfolder2/module-b') { name = 'module-b-2' }
        """
        file('subfolder1/module-b').mkdirs()
        file('subfolder2/module-b').mkdirs()

        when:
        def eclipseProjects = withConnection { action(new FetchEclipseProjects()).run() }

        then:
        eclipseProjects.collect { it.name } == ['module-main', 'module-main-module-b-1', 'module-main-module-b-2']
    }

    def "uses name given to included build as IDEA module name if there is no name conflict"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            includeBuild('module-b-folder')
        """
        file('module-b-folder').mkdir()
        file('module-b-folder/settings.gradle') << """
            rootProject.name = 'module-b-name'
        """

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-main', 'module-b-name']

        allProjects.getIdeaProject('module-main').modules.collect { it.name } == ['module-main']
        allProjects.getIdeaProject('module-b-folder') == null
        allProjects.getIdeaProject('module-b-name').modules.collect { it.name } == ['module-b-name']
    }

    def "can resolve name conflict between subproject and included build by using the identity name of the included build"() {
        given:
        settingsFile << """
            rootProject.name = 'module-main'
            includeBuild('module-b-folder')
            include('module-b-name')
        """
        file('module-b-folder').mkdir()
        file('module-b-folder/settings.gradle') << """
            rootProject.name = 'module-b-name'
        """

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-main', 'module-b-name']

        allProjects.getIdeaProject('module-main').modules.collect { it.name } == ['module-main', 'module-main-module-b-name']
        allProjects.getIdeaProject('module-b-folder') == null
        allProjects.getIdeaProject('module-b-name').modules.collect { it.name } == ['module-main-module-b-folder']
    }
}
