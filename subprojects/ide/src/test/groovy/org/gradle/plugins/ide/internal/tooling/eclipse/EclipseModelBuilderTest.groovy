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

package org.gradle.plugins.ide.internal.tooling.eclipse

import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.internal.tooling.EclipseModelBuilder
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder
import org.gradle.tooling.internal.gradle.DefaultGradleProject
import org.gradle.util.TestUtil
import spock.lang.Shared
import spock.lang.Specification

class EclipseModelBuilderTest extends Specification {

    @Shared
    def project = TestUtil.builder().withName("project").build()
    @Shared
    def child1 = TestUtil.builder().withName("child1").withParent(project).build()
    @Shared
    def child2 = TestUtil.builder().withName("child2").withParent(project).build()

    def setupSpec() {
        [project, child1, child2].each{ it.pluginManager.apply(EclipsePlugin.class) }
    }

    def "can read natures"() {
        setup:
        project.eclipse.project.natures = ['nature.a', 'nature.b']
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.projectNatures.collect{ it.id } == ['nature.a', 'nature.b']
    }

    def "nature list independent from project hierarchy"() {
        setup:
        project.eclipse.project.natures = ['nature.for.root']
        child1.eclipse.project.natures = ['nature.for.child1']
        child2.eclipse.project.natures = ['nature.for.child2']
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.projectNatures.collect{ it.id } == ['nature.for.root']
        eclipseModel.children[0].name == 'child1'
        eclipseModel.children[0].projectNatures.collect{ it.id } == ['nature.for.child1']
        eclipseModel.children[1].name == 'child2'
        eclipseModel.children[1].projectNatures.collect{ it.id } == ['nature.for.child2']
    }

    def "can read build commands"() {
        setup:
        project.eclipse.project.buildCommands = [
            new BuildCommand('buildCommandWithoutArguments', [:]),
            new BuildCommand('buildCommandWithArguments', ['argumentOneKey': 'argumentOneValue', 'argumentTwoKey': 'argumentTwoValue'])
        ]
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.buildCommands.collect{ it.name } == ['buildCommandWithoutArguments', 'buildCommandWithArguments']
        eclipseModel.buildCommands.collect{ it.arguments } == [[:], ['argumentOneKey': 'argumentOneValue', 'argumentTwoKey': 'argumentTwoValue']]
    }

    def "build command list independent from project hierarchy"() {
        setup:
        project.eclipse.project.buildCommands = [new BuildCommand('rootBuildCommand', ['rootKey': 'rootValue'])]
        child1.eclipse.project.buildCommands = [new BuildCommand('child1BuildCommand', ['child1Key': 'child1Value'])]
        child2.eclipse.project.buildCommands = [new BuildCommand('child2BuildCommand', ['child2Key': 'child2Value'])]
        def modelBuilder = createEclipseModelBuilder()

        when:
        def eclipseModel = modelBuilder.buildAll("org.gradle.tooling.model.eclipse.EclipseProject", project)

        then:
        eclipseModel.buildCommands.collect{ it.name } == ['rootBuildCommand']
        eclipseModel.buildCommands.collect{ it.arguments } == [ ['rootKey': 'rootValue'] ]
        eclipseModel.children[0].name == 'child1'
        eclipseModel.children[0].buildCommands.collect{ it.name } == ['child1BuildCommand']
        eclipseModel.children[0].buildCommands.collect{ it.arguments } == [['child1Key': 'child1Value']]
        eclipseModel.children[1].name == 'child2'
        eclipseModel.children[1].buildCommands.collect{ it.name } == ['child2BuildCommand']
        eclipseModel.children[1].buildCommands.collect{ it.arguments } == [['child2Key': 'child2Value']]
    }

    private def createEclipseModelBuilder() {
        def gradleProjectBuilder = Mock(GradleProjectBuilder)
        gradleProjectBuilder.buildAll(_) >> Mock(DefaultGradleProject)
        new EclipseModelBuilder(gradleProjectBuilder)
    }
}
