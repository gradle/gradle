/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class VisualStudioProjectRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def fileResolver = Mock(FileResolver)
    def ideArtifactRegistry = Mock(IdeArtifactRegistry)
    def registry = new VisualStudioProjectRegistry(fileResolver, TestUtil.instantiatorFactory().decorateLenient(), ideArtifactRegistry, CollectionCallbackActionDecorator.NOOP, TestUtil.objectFactory(), new DefaultProviderFactory())

    def "creates a matching visual studio project configuration for target binary"() {
        def executableBinary = targetBinary("vsConfig")
        when:
        registry.addProjectConfiguration(executableBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.type == "Makefile"
        vsConfig.project.name == "mainExe"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "Win32"
    }

    def "adds ide artifact when project and projectConfiguration are added"() {
        def executableBinary = targetBinary("vsConfig")
        def metadata = null

        when:
        registry.addProjectConfiguration(executableBinary)

        then:
        1 * ideArtifactRegistry.registerIdeProject(_) >> { VisualStudioProjectMetadata m ->
            metadata = m
        }
        metadata.name == "mainExe"
        metadata.configurations*.name == ["vsConfig|Win32"]
        metadata.configurations*.buildable == [true]
    }

    def "returns same visual studio project configuration for native binaries that share project name"() {
        def executableBinary1 = targetBinary("vsConfig1")
        def executableBinary2 = targetBinary("vsConfig2")

        when:
        registry.addProjectConfiguration(executableBinary1)
        registry.addProjectConfiguration(executableBinary2)

        then:
        def vsConfig1 = registry.getProjectConfiguration(executableBinary1)
        def vsConfig2 = registry.getProjectConfiguration(executableBinary2)
        vsConfig1.project == vsConfig2.project

        and:
        vsConfig1.type == "Makefile"
        vsConfig1.project.name == "mainExe"
        vsConfig1.configurationName == "vsConfig1"
        vsConfig1.platformName == "Win32"

        and:
        vsConfig2.type == "Makefile"
        vsConfig2.project.name == "mainExe"
        vsConfig2.configurationName == "vsConfig2"
        vsConfig2.platformName == "Win32"
    }

    def "visual studio project contains sources for native binaries for all configurations"() {
        def sourceCommon = file("source")
        def source1 = file("source1")
        def source2 = file("source2")
        def executableBinary1 = targetBinary("vsConfig1", sourceCommon, source1)
        def executableBinary2 = targetBinary("vsConfig2", sourceCommon, source2)

        when:
        registry.addProjectConfiguration(executableBinary1)
        registry.addProjectConfiguration(executableBinary2)

        then:
        def vsProject = registry.getProjectConfiguration(executableBinary1).project
        vsProject.sourceFiles.files == [sourceCommon, source1, source2] as Set
    }

    private VisualStudioTargetBinary targetBinary(String variant, File... sources) {
        def targetBinary = Mock(VisualStudioTargetBinary)
        targetBinary.getSourceFiles() >> fileCollection(sources)
        targetBinary.getHeaderFiles() >> fileCollection()
        targetBinary.getResourceFiles() >> fileCollection()
        targetBinary.projectPath >> ":"
        targetBinary.componentName >> "main"
        targetBinary.visualStudioProjectName >> "mainExe"
        targetBinary.visualStudioConfigurationName >> variant
        targetBinary.projectType >> VisualStudioTargetBinary.ProjectType.EXE
        targetBinary.variantDimensions >> [variant]
        return targetBinary
    }

    private FileCollection fileCollection(File... files = []) {
        return Stub(FileCollection) {
            getFiles() >> (files as Set)
        }
    }

    private File file(Object... path) {
        return tmpDir.file(path)
    }
}
