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
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class VisualStudioProjectRegistryTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def projectIdentifier = Stub(ProjectIdentifier)
    def visualStudioProjectMapper = Mock(VisualStudioProjectMapper)
    def registry = new VisualStudioProjectRegistry(projectIdentifier, fileResolver, visualStudioProjectMapper, DirectInstantiator.INSTANCE)

    def "creates a matching visual studio project configuration for target binary"() {
        def executableBinary = targetBinary()
        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")

        and:
        registry.addProjectConfiguration(executableBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.type == "Makefile"
        vsConfig.project.name == "vsProject"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "vsPlatform"
    }

    def "returns same visual studio project configuration for native binaries that share project name"() {
        def executableBinary1 = targetBinary()
        def executableBinary2 = targetBinary()

        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary1) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig1", "vsPlatform")
        visualStudioProjectMapper.mapToConfiguration(executableBinary2) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig2", "vsPlatform")

        and:
        registry.addProjectConfiguration(executableBinary1)
        registry.addProjectConfiguration(executableBinary2)

        then:
        def vsConfig1 = registry.getProjectConfiguration(executableBinary1)
        def vsConfig2 = registry.getProjectConfiguration(executableBinary2)
        vsConfig1.project == vsConfig2.project

        and:
        vsConfig1.type == "Makefile"
        vsConfig1.project.name == "vsProject"
        vsConfig1.configurationName == "vsConfig1"
        vsConfig1.platformName == "vsPlatform"

        and:
        vsConfig2.type == "Makefile"
        vsConfig2.project.name == "vsProject"
        vsConfig2.configurationName == "vsConfig2"
        vsConfig2.platformName == "vsPlatform"
    }

    def "visual studio project contains sources for native binaries for all configurations"() {
        def sourceCommon = Mock(File)
        def source1 = Mock(File)
        def source2 = Mock(File)
        def executableBinary1 = targetBinary(sourceCommon, source1)
        def executableBinary2 = targetBinary(sourceCommon, source2)

        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary1) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig1", "vsPlatform")
        visualStudioProjectMapper.mapToConfiguration(executableBinary2) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig2", "vsPlatform")

        and:
        registry.addProjectConfiguration(executableBinary1)
        registry.addProjectConfiguration(executableBinary2)

        then:
        def vsProject = registry.getProjectConfiguration(executableBinary1).project
        vsProject.sourceFiles == [sourceCommon, source1, source2] as Set
    }

    private VisualStudioTargetBinary targetBinary(File... sources) {
        def targetBinary = Mock(VisualStudioTargetBinary)
        targetBinary.getSourceFiles() >> fileCollection(sources)
        targetBinary.getHeaderFiles() >> fileCollection()
        targetBinary.getResourceFiles() >> fileCollection()
        targetBinary.projectPath >> ":"
        targetBinary.componentName >> "main"
        return targetBinary
    }
    private FileCollection fileCollection(File... files = []) {
        return Stub(FileCollection) {
            getFiles() >> (files as Set)
        }
    }
}
