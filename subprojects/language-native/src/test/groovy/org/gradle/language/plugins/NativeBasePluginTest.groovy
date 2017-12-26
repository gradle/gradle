/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.plugins

import org.gradle.api.Task
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithInstallation
import org.gradle.language.ComponentWithLinkFile
import org.gradle.language.ComponentWithRuntimeFile
import org.gradle.language.ProductionComponent
import org.gradle.language.internal.DefaultNativeBinaryContainer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class NativeBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testComponent").build()

    def "registers each binary of a component as it becomes known"() {
        def b1 = Stub(SoftwareComponent)
        b1.name >> "b1"
        def b2 = Stub(SoftwareComponent)
        b2.name >> "b2"
        def component = Stub(ComponentWithBinaries)
        def binaries = new DefaultNativeBinaryContainer(SoftwareComponent, null)
        component.binaries >> binaries

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)

        then:
        project.components.size() == 1

        when:
        binaries.add(b1)
        binaries.add(b2)

        then:
        project.components.size() == 3
        project.components.b1 == b1
        project.components.b2 == b2
    }

    def "assemble task does nothing when no main component"() {
        def component = Stub(SoftwareComponent)
        component.name >> 'not-main'

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)

        then:
        project.tasks['assemble'] TaskDependencyMatchers.dependsOn()
    }

    def "assemble task builds installation of development binary of main component"() {
        def installTask = Stub(Task)
        installTask.name >> 'mainInstall'
        def installDir = Stub(TestProvider)
        installDir.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(installTask)
        }
        def binary = Stub(ComponentWithInstallation)
        binary.installDirectory >> installDir
        def component = Stub(ProductionComponent)
        component.name >> 'main'
        component.developmentBinary >> Providers.of(binary)

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)

        then:
        project.tasks['assemble'] TaskDependencyMatchers.dependsOn('mainInstall')
    }

    def "assemble task builds runtime files of development binary of main component"() {
        def linkTask = Stub(Task)
        linkTask.name >> 'mainLink'
        def runtimeFile = Stub(TestProvider)
        runtimeFile.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(linkTask)
        }
        def binary = Stub(ComponentWithRuntimeFile)
        binary.runtimeFile >> runtimeFile
        def component = Stub(ProductionComponent)
        component.name >> 'main'
        component.developmentBinary >> Providers.of(binary)

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)

        then:
        project.tasks['assemble'] TaskDependencyMatchers.dependsOn('mainLink')
    }

    def "assemble task builds link files of development binary of main component"() {
        def linkTask = Stub(Task)
        linkTask.name >> 'mainLink'
        def linkFile = Stub(TestProvider)
        linkFile.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(linkTask)
        }
        def binary = Stub(ComponentWithLinkFile)
        binary.linkFile >> linkFile
        def component = Stub(ProductionComponent)
        component.name >> 'main'
        component.developmentBinary >> Providers.of(binary)

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)

        then:
        project.tasks['assemble'] TaskDependencyMatchers.dependsOn('mainLink')
    }

    interface TestProvider extends ProviderInternal<FileSystemLocation>, TaskDependencyContainer {
    }
}
