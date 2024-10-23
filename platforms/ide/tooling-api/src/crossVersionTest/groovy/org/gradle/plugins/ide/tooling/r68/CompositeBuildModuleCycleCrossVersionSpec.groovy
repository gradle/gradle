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
import org.gradle.plugins.ide.tooling.r31.IdeaProjectUtil
import org.gradle.plugins.ide.tooling.r33.FetchEclipseProjects
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

@TargetGradleVersion(">=6.8")
class CompositeBuildModuleCycleCrossVersionSpec extends ToolingApiSpecification {

    private compositeWithDirectIncludeCycle() {
        settingsFile << """
            rootProject.name = 'module-root'
            includeBuild('module-a')
        """
        file('module-a').mkdir()
        file('module-b').mkdir()
        file('module-a/settings.gradle') << """
            includeBuild('../module-b')
        """
        file('module-b/settings.gradle') << """
            includeBuild('../module-a')
        """
    }

    private compositeWithIndirectIncludeCycle() {
        settingsFile << """
            rootProject.name = 'module-root'
            includeBuild('module-a')
        """
        file('module-a').mkdir()
        file('module-b').mkdir()
        file('module-c').mkdir()
        file('module-a/settings.gradle') << """
            includeBuild('../module-b')
        """
        file('module-b/settings.gradle') << """
            includeBuild('../module-c')
        """
        file('module-c/settings.gradle') << """
            includeBuild('../module-a')
        """
    }

    private compositeWithRootInvolvingIncludeCycle() {
        settingsFile << """
            rootProject.name = 'module-root'
            includeBuild('module-a')
        """
        file('module-a').mkdir()
        file('module-a/settings.gradle') << """
            includeBuild('..')
        """
    }

    def "IDEA can handle direct cycles between included builds"() {
        given:
        compositeWithDirectIncludeCycle()

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-root', 'module-a', 'module-b']
        allProjects.includedBuildIdeaProjects.keySet().collect { it.buildIdentifier.rootDir.name } == ['module-a', 'module-b']
    }

    def "IDEA can handle indirect cycles between included builds"() {
        given:
        compositeWithIndirectIncludeCycle()

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-root', 'module-a', 'module-b', 'module-c']
        allProjects.includedBuildIdeaProjects.values().collect { it.name } == ['module-a', 'module-b', 'module-c']
    }

    def "IDEA can handle cycles involving the root build"() {
        given:
        compositeWithRootInvolvingIncludeCycle()

        when:
        def allProjects = withConnection {c -> c.action(new IdeaProjectUtil.GetAllIdeaProjectsAction()).run() }

        then:
        allProjects.allIdeaProjects.collect { it.name } == ['module-root', 'module-a']
        allProjects.includedBuildIdeaProjects.values().collect { it.name } == ['module-a', 'module-root']
    }

    def "Eclipse can handle cycles between included builds"() {
        given:
        compositeWithDirectIncludeCycle()

        when:
        def eclipseProjects = withConnection { action(new FetchEclipseProjects()).run() }

        then:
        eclipseProjects.collect { it.name } == ['module-root', 'module-a', 'module-b']
    }

    def "Eclipse can handle indirect cycles between included builds"() {
        given:
        compositeWithIndirectIncludeCycle()

        when:
        def eclipseProjects = withConnection { action(new FetchEclipseProjects()).run() }

        then:
        eclipseProjects.collect { it.name } == ['module-root', 'module-a', 'module-b', 'module-c']
    }

    def "Eclipse can handle cycles involving the root build"() {
        given:
        compositeWithRootInvolvingIncludeCycle()

        when:
        def eclipseProjects = withConnection { action(new FetchEclipseProjects()).run() }

        then:
        eclipseProjects.collect { it.name } == ['module-root', 'module-a']
    }

    def "Eclipse model builder can handle cycles between included builds"() {
        given:
        compositeWithDirectIncludeCycle()

        when:
        def model = withConnection { getModel(HierarchicalEclipseProject) }

        then:
        model.name == 'module-root'
    }

    def "Eclipse model builder can handle indirect cycles between included builds"() {
        given:
        compositeWithIndirectIncludeCycle()

        when:
        def model = withConnection { getModel(HierarchicalEclipseProject) }

        then:
        model.name == 'module-root'
    }

    def "Eclipse model builder can handle cycles involving the root build"() {
        given:
        compositeWithRootInvolvingIncludeCycle()

        when:
        def model = withConnection { getModel(HierarchicalEclipseProject) }

        then:
        model.name == 'module-root'
    }
}
