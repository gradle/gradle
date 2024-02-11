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
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.component.PublishableComponent
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.component.DefaultSoftwareComponentVariant
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.ComponentWithBinaries
import org.gradle.language.ComponentWithOutputs
import org.gradle.language.ProductionComponent
import org.gradle.language.internal.DefaultBinaryCollection
import org.gradle.language.nativeplatform.internal.ComponentWithNames
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithSharedLibrary
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.ExtractSymbols
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.StripSymbols
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class NativeBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testComponent").build()

    def "registers each binary of a component as it becomes known"() {
        def b1 = Stub(SoftwareComponent)
        b1.name >> "b1"
        def b2 = Stub(SoftwareComponent)
        b2.name >> "b2"
        def component = Stub(ComponentWithBinaries)
        def binaries = new DefaultBinaryCollection(SoftwareComponent)
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
        binaries.realizeNow()

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
        def binaries = new DefaultBinaryCollection(SoftwareComponent)
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
        def binaries = new DefaultBinaryCollection(SoftwareComponent)
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

    def "adds tasks to assemble a static library"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getStaticLibraryName(_) >> { String p -> p + ".lib" }

        def linkFileProp = project.objects.fileProperty()
        def linkFileTasKProp = project.objects.property(Task)
        def createTaskProp = project.objects.property(CreateStaticLibrary)

        def staticLib = Stub(ConfigurableComponentWithStaticLibrary)
        staticLib.name >> "windowsDebug"
        staticLib.names >> Names.of("windowsDebug")
        staticLib.nativePlatform >> Stub(NativePlatformInternal)
        staticLib.toolChain >> Stub(NativeToolChainInternal)
        staticLib.platformToolProvider >> toolProvider
        staticLib.baseName >> Providers.of("test_lib")
        staticLib.linkFile >> linkFileProp
        staticLib.linkFileProducer >> linkFileTasKProp
        staticLib.createTask >> createTaskProp

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(staticLib)

        expect:
        def createTask = project.tasks['createWindowsDebug']
        createTask instanceof CreateStaticLibrary
        createTask.binaryFile.get().asFile == projectDir.file("build/lib/windows/debug/test_lib.lib")

        and:
        linkFileProp.get().asFile == createTask.binaryFile.get().asFile
        createTaskProp.get() == createTask
        linkFileTasKProp.get() == createTask
    }

    def "adds tasks to assemble a shared library"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getSharedLibraryName(_) >> { String p -> p + ".dll" }

        def runtimeFileProp = project.objects.fileProperty()
        def linkTaskProp = project.objects.property(LinkSharedLibrary)
        def linkFileTasKProp = project.objects.property(Task)

        def sharedLibrary = Stub(ConfigurableComponentWithSharedLibrary)
        sharedLibrary.name >> "windowsDebug"
        sharedLibrary.names >> Names.of("windowsDebug")
        sharedLibrary.nativePlatform >> Stub(NativePlatformInternal)
        sharedLibrary.toolChain >> Stub(NativeToolChainInternal)
        sharedLibrary.platformToolProvider >> toolProvider
        sharedLibrary.baseName >> Providers.of("test_lib")
        sharedLibrary.runtimeFile >> runtimeFileProp
        sharedLibrary.linkTask >> linkTaskProp
        sharedLibrary.linkFileProducer >> linkFileTasKProp

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(sharedLibrary)

        expect:
        def linkTask = project.tasks['linkWindowsDebug']
        linkTask instanceof LinkSharedLibrary
        linkTask.linkedFile.get().asFile == projectDir.file("build/lib/windows/debug/test_lib.dll")

        and:
        runtimeFileProp.get().asFile == linkTask.linkedFile.get().asFile
        linkTaskProp.get() == linkTask
        linkFileTasKProp.get() == linkTask
    }

    def "adds tasks to assemble and strip a shared library"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getSharedLibraryName(_) >> { String p -> p + ".dll" }
        toolProvider.getLibrarySymbolFileName(_) >> { String p -> p + ".dll.pdb" }
        toolProvider.requiresDebugBinaryStripping() >> true

        def runtimeFileProp = project.objects.fileProperty()
        def linkTaskProp = project.objects.property(LinkSharedLibrary)
        def linkFileTasKProp = project.objects.property(Task)

        def sharedLibrary = Stub(ConfigurableComponentWithSharedLibrary)
        sharedLibrary.name >> "windowsDebug"
        sharedLibrary.names >> Names.of("windowsDebug")
        sharedLibrary.debuggable >> true
        sharedLibrary.optimized >> true
        sharedLibrary.nativePlatform >> Stub(NativePlatformInternal)
        sharedLibrary.toolChain >> Stub(NativeToolChainInternal)
        sharedLibrary.platformToolProvider >> toolProvider
        sharedLibrary.baseName >> Providers.of("test_lib")
        sharedLibrary.runtimeFile >> runtimeFileProp
        sharedLibrary.linkTask >> linkTaskProp
        sharedLibrary.linkFileProducer >> linkFileTasKProp

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(sharedLibrary)

        expect:
        def linkTask = project.tasks['linkWindowsDebug']
        linkTask instanceof LinkSharedLibrary
        linkTask.linkedFile.get().asFile == projectDir.file("build/lib/windows/debug/test_lib.dll")

        def stripTask = project.tasks['stripSymbolsWindowsDebug']
        stripTask instanceof StripSymbols
        stripTask.binaryFile.get().asFile == linkTask.linkedFile.get().asFile
        stripTask.outputFile.get().asFile == projectDir.file("build/lib/windows/debug/stripped/test_lib.dll")

        def extractTask = project.tasks['extractSymbolsWindowsDebug']
        extractTask instanceof ExtractSymbols
        extractTask.binaryFile.get().asFile == linkTask.linkedFile.get().asFile
        extractTask.symbolFile.get().asFile == projectDir.file("build/lib/windows/debug/stripped/test_lib.dll.pdb")

        and:
        runtimeFileProp.get().asFile == stripTask.outputFile.get().asFile
        linkTaskProp.get() == linkTask
        linkFileTasKProp.get() == stripTask
    }

    def "adds tasks to assemble an executable"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getExecutableName(_) >> { String p -> p + ".exe" }

        def exeFileProp = project.objects.fileProperty()
        def exeFileTaskProp = project.objects.property(Task)
        def debugExeFileProp = project.objects.fileProperty()
        def linkTaskProp = project.objects.property(LinkExecutable)
        def installDirProp = project.objects.directoryProperty()
        def installTaskProp = project.objects.property(InstallExecutable)

        def executable = Stub(ConfigurableComponentWithExecutable)
        executable.name >> "windowsDebug"
        executable.names >> Names.of("windowsDebug")
        executable.nativePlatform >> Stub(NativePlatformInternal)
        executable.toolChain >> Stub(NativeToolChainInternal)
        executable.platformToolProvider >> toolProvider
        executable.baseName >> Providers.of("test_app")
        executable.executableFile >> exeFileProp
        executable.executableFileProducer >> exeFileTaskProp
        executable.debuggerExecutableFile >> debugExeFileProp
        executable.linkTask >> linkTaskProp
        executable.installDirectory >> installDirProp
        executable.installTask >> installTaskProp

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(executable)

        expect:
        def linkTask = project.tasks['linkWindowsDebug']
        linkTask instanceof LinkExecutable
        linkTask.linkedFile.get().asFile == projectDir.file("build/exe/windows/debug/test_app.exe")

        def installTask = project.tasks['installWindowsDebug']
        installTask instanceof InstallExecutable
        installTask.executableFile.get().asFile == linkTask.linkedFile.get().asFile
        installTask.installDirectory.get().asFile == projectDir.file("build/install/windows/debug")

        and:
        exeFileProp.get().asFile == linkTask.linkedFile.get().asFile
        debugExeFileProp.get().asFile == installTask.installedExecutable.get().asFile

        linkTaskProp.get() == linkTask
        exeFileTaskProp.get() == linkTask

        and:
        installDirProp.get().asFile == installTask.installDirectory.get().asFile
        installTaskProp.get() == installTask
    }

    def "adds tasks to assemble and strip an executable"() {
        def toolProvider = Stub(PlatformToolProvider)
        toolProvider.getExecutableName(_) >> { String p -> p + ".exe" }
        toolProvider.getExecutableSymbolFileName(_) >> { String p -> p + ".exe.pdb" }
        toolProvider.requiresDebugBinaryStripping() >> true

        def exeFileProp = project.objects.fileProperty()
        def exeFileTaskProp = project.objects.property(Task)
        def debugExeFileProp = project.objects.fileProperty()
        def linkTaskProp = project.objects.property(LinkExecutable)
        def installDirProp = project.objects.directoryProperty()
        def installTaskProp = project.objects.property(InstallExecutable)

        def executable = Stub(ConfigurableComponentWithExecutable)
        executable.name >> "windowsDebug"
        executable.names >> Names.of("windowsDebug")
        executable.debuggable >> true
        executable.optimized >> true
        executable.nativePlatform >> Stub(NativePlatformInternal)
        executable.toolChain >> Stub(NativeToolChainInternal)
        executable.platformToolProvider >> toolProvider
        executable.baseName >> Providers.of("test_app")
        executable.executableFile >> exeFileProp
        executable.executableFileProducer >> exeFileTaskProp
        executable.debuggerExecutableFile >> debugExeFileProp
        executable.linkTask >> linkTaskProp
        executable.installDirectory >> installDirProp
        executable.installTask >> installTaskProp

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(executable)

        expect:
        def linkTask = project.tasks['linkWindowsDebug']
        linkTask instanceof LinkExecutable
        linkTask.linkedFile.get().asFile == projectDir.file("build/exe/windows/debug/test_app.exe")

        def stripTask = project.tasks['stripSymbolsWindowsDebug']
        stripTask instanceof StripSymbols
        stripTask.binaryFile.get().asFile == linkTask.linkedFile.get().asFile
        stripTask.outputFile.get().asFile == projectDir.file("build/exe/windows/debug/stripped/test_app.exe")

        def extractTask = project.tasks['extractSymbolsWindowsDebug']
        extractTask instanceof ExtractSymbols
        extractTask.binaryFile.get().asFile == linkTask.linkedFile.get().asFile
        extractTask.symbolFile.get().asFile == projectDir.file("build/exe/windows/debug/stripped/test_app.exe.pdb")

        def installTask = project.tasks['installWindowsDebug']
        installTask instanceof InstallExecutable
        installTask.executableFile.get().asFile == stripTask.outputFile.get().asFile
        installTask.installDirectory.get().asFile == projectDir.file("build/install/windows/debug")

        and:
        exeFileProp.get().asFile == stripTask.outputFile.get().asFile
        debugExeFileProp.get().asFile == installTask.installedExecutable.get().asFile

        linkTaskProp.get() == linkTask
        exeFileTaskProp.get() == stripTask

        and:
        installDirProp.get().asFile == installTask.installDirectory.get().asFile
        installTaskProp.get() == installTask
    }

    def "adds outgoing configuration for component with link usage"() {
        def component = Stub(ConfigurableComponentWithLinkUsage)
        component.name >> "debugWindows"
        component.names >> Names.of("debugWindows")
        component.implementationDependencies >> Stub(ConfigurationInternal)
        component.linkFile >> project.objects.fileProperty()

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(component)

        expect:
        project.configurations['debugWindowsLinkElements']
    }

    def "adds outgoing configuration for component with runtime usage"() {
        def component = Stub(ConfigurableComponentWithRuntimeUsage)
        component.name >> "debugWindows"
        component.names >> Names.of("debugWindows")
        component.implementationDependencies >> Stub(ConfigurationInternal)

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.components.add(component)

        expect:
        project.configurations['debugWindowsRuntimeElements']
    }

    def "adds Maven publications for component with main publication"() {
        def artifact1 = Stub(PublishArtifact)
        def variant1 = new DefaultSoftwareComponentVariant('variant1', ImmutableAttributes.EMPTY, [artifact1] as Set)
        artifact1.getFile() >> projectDir.file("artifact1")
        variant1.artifacts >> [artifact1]
        def publishableVariant1 = Stub(PublishableVariant)
        publishableVariant1.name >> "debug"
        publishableVariant1.usages >> [variant1]
        publishableVariant1.getCoordinates() >> new DefaultModuleVersionIdentifier("my.group", "test_app_debug", "1.2")

        def artifact2 = Stub(PublishArtifact)
        def variant2 = new DefaultSoftwareComponentVariant('variant2', ImmutableAttributes.EMPTY, [artifact2] as Set)
        artifact2.getFile() >> projectDir.file("artifact1")
        def publishableVariant2 = Stub(PublishableVariant)
        publishableVariant2.name >> "release"
        publishableVariant2.usages >> [variant2]
        publishableVariant2.getCoordinates() >> new DefaultModuleVersionIdentifier("my.group", "test_app_release", "1.2")

        def doNotPublish = Stub(SoftwareComponentInternal)

        def mainVariant = Stub(TestVariant)
        mainVariant.name >> "main"
        mainVariant.variants >> [publishableVariant1, publishableVariant2, doNotPublish]

        def component = Stub(PublicationAwareComponent)
        component.mainPublication >> mainVariant
        component.baseName >> Providers.of('test_app')

        given:
        project.pluginManager.apply(NativeBasePlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.components.add(component)
        project.group = "my.group"
        project.version = "1.2"
        ((ProjectInternal) project).evaluate()

        expect:
        def publishing = project.publishing
        publishing.publications.size() == 3

        def main = publishing.publications.main
        main.groupId == 'my.group'
        main.artifactId == 'test_app'
        main.version == '1.2'
        main.artifacts.empty

        def debug = publishing.publications.debug
        debug.groupId == 'my.group'
        debug.artifactId == 'test_app_debug'
        debug.version == '1.2'
        debug.artifacts.size() == 1

        def release = publishing.publications.release
        release.groupId == 'my.group'
        release.artifactId == 'test_app_release'
        release.version == '1.2'
        release.artifacts.size() == 1
    }

    private ComponentWithOutputs binary(String name, String taskName) {
        def outputs = fileCollection(taskName)
        def binary = Stub(TestBinary)
        binary.name >> name
        binary.names >> Names.of(name)
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

    interface TestBinary extends ComponentWithOutputs, ComponentWithNames {
    }

    interface TestComponent extends ProductionComponent, ComponentWithBinaries {
    }

    interface TestVariant extends ComponentWithVariants, SoftwareComponentInternal {
    }

    interface PublishableVariant extends PublishableComponent, SoftwareComponentInternal {
    }
}
