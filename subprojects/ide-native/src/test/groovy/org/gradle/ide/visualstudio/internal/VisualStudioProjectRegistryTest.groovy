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

import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal
import spock.lang.Specification

class VisualStudioProjectRegistryTest extends Specification {
    private Set<LanguageSourceSet> sources = new DefaultDomainObjectSet<>(LanguageSourceSet)
    def fileResolver = Mock(FileResolver)
    def visualStudioProjectMapper = Mock(VisualStudioProjectMapper)
    def registry = new VisualStudioProjectRegistry(fileResolver, visualStudioProjectMapper, DirectInstantiator.INSTANCE)

    def executable = Mock(NativeExecutableSpec)

    def "creates a matching visual studio project configuration for NativeBinary"() {
        def executableBinary = Mock(NativeExecutableBinarySpecInternal)
        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")
        executableBinary.component >> executable
        executableBinary.inputs >> sources

        and:
        registry.addProjectConfiguration(executableBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.project.component == executable
        vsConfig.type == "Makefile"
        vsConfig.project.name == "vsProject"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "vsPlatform"
    }

    def "returns same visual studio project configuration for native binaries that share project name"() {
        def executableBinary1 = Mock(NativeExecutableBinarySpecInternal)
        def executableBinary2 = Mock(NativeExecutableBinarySpecInternal)

        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary1) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig1", "vsPlatform")
        visualStudioProjectMapper.mapToConfiguration(executableBinary2) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig2", "vsPlatform")
        executableBinary1.inputs >> sources
        executableBinary2.inputs >> sources

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
        def executableBinary1 = Mock(NativeExecutableBinarySpecInternal)
        def executableBinary2 = Mock(NativeExecutableBinarySpecInternal)
        def sourceCommon = Mock(LanguageSourceSet)
        def source1 = Mock(LanguageSourceSet)
        def source2 = Mock(LanguageSourceSet)

        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary1) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig1", "vsPlatform")
        visualStudioProjectMapper.mapToConfiguration(executableBinary2) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig2", "vsPlatform")
        executableBinary1.inputs >> new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet, [sourceCommon, source1])
        executableBinary2.inputs >> new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet, [sourceCommon, source2])

        and:
        registry.addProjectConfiguration(executableBinary1)
        registry.addProjectConfiguration(executableBinary2)

        then:
        def vsProject = registry.getProjectConfiguration(executableBinary1).project
        vsProject.sources as List == [sourceCommon, source1, source2]
    }
}
