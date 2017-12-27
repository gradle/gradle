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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.provider.Providers
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithOutputs
import org.gradle.language.ProductionComponent
import org.gradle.language.internal.DefaultBinaryCollection
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
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
        def binaries = new DefaultBinaryCollection(SoftwareComponent, null)
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

    def "assemble task builds outputs of development binary of main component"() {
        def binary1 = binary('debug', 'debugInstall')
        def binary2 = binary('release', 'releaseInstall')
        def binaries = new DefaultBinaryCollection(SoftwareComponent, null)
        binaries.add(binary1)
        binaries.add(binary2)
        def component = Stub(TestComponent)
        component.binaries >> binaries
        component.developmentBinary >> Providers.of(binary1)

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)
        binaries.realizeNow()

        then:
        project.tasks['assemble'] TaskDependencyMatchers.dependsOn('debugInstall')
    }

    def "adds assemble task for each binary of main component"() {
        def binary1 = binary('debug', 'installDebug')
        def binary2 = binary('release', 'installRelease')
        def binaries = new DefaultBinaryCollection(SoftwareComponent, null)
        binaries.add(binary1)
        binaries.add(binary2)
        def component = Stub(TestComponent)
        component.binaries >> binaries
        component.developmentBinary >> Providers.of(binary1)

        given:
        project.pluginManager.apply(NativeBasePlugin)

        when:
        project.components.add(component)
        binaries.realizeNow()

        then:
        project.tasks['assembleDebug'] TaskDependencyMatchers.dependsOn('installDebug')
        project.tasks['assembleRelease'] TaskDependencyMatchers.dependsOn('installRelease')
    }

    def "adds tasks to assemble static library"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getStaticLibraryName(_) >> { String p -> p + ".lib" }

        def linkFileProp = project.objects.property(RegularFile)
        def createTaskProp = project.objects.property(CreateStaticLibrary)

        def staticLib = Stub(ConfigurableComponentWithStaticLibrary)
        staticLib.name >> "windowsDebug"
        staticLib.targetPlatform >> Stub(NativePlatformInternal)
        staticLib.toolChain >> Stub(NativeToolChainInternal)
        staticLib.platformToolProvider >> toolProvider
        staticLib.baseName >> Providers.of("test_lib")
        staticLib.linkFile >> linkFileProp
        staticLib.createTask >> createTaskProp

        def binaries = new DefaultBinaryCollection(SoftwareComponent, null)
        binaries.add(staticLib)
        def component = Stub(TestComponent)
        component.binaries >> binaries
        component.developmentBinary >> Providers.of(staticLib)

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(component)
        binaries.realizeNow()

        expect:
        def createTask = project.tasks['createWindowsDebug']
        createTask instanceof CreateStaticLibrary
        createTask.binaryFile.get().asFile == projectDir.file("build/lib/windows/debug/test_lib.lib")

        and:
        linkFileProp.get().asFile == createTask.binaryFile.get().asFile
        createTaskProp.get() == createTask
    }

    private ComponentWithOutputs binary(String name, String taskName) {
        def outputs = fileCollection(taskName)
        def binary = Stub(ComponentWithOutputs)
        binary.name >> name
        binary.outputs >> outputs
        return binary
    }

    private FileCollection fileCollection(String taskName) {
        def installTask = Stub(Task)
        installTask.name >> taskName
        def deps = Stub(TaskDependency)
        deps.getDependencies(_) >> [installTask]
        def outputs = Stub(FileCollection)
        outputs.buildDependencies >> deps
        return outputs
    }

    interface TestComponent extends ProductionComponent, ComponentWithBinaries {
    }
}
