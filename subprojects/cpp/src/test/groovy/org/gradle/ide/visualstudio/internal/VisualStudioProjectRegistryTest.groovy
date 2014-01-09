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
import org.gradle.nativebinaries.ExecutableBinary
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.StaticLibraryBinary
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import spock.lang.Specification

class VisualStudioProjectRegistryTest extends Specification {
    private DefaultDomainObjectSet<LanguageSourceSet> sources = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet)
    def fileResolver = Mock(FileResolver)
    def visualStudioProjectResolver = Mock(VisualStudioProjectResolver)
    def visualStudioProjectMapper = Mock(VisualStudioProjectMapper)
    def registry = new VisualStudioProjectRegistry(fileResolver, visualStudioProjectResolver, visualStudioProjectMapper, new DirectInstantiator())

    def executableBinary = Mock(ExecutableInternal)
    def sharedLibraryBinary = Mock(SharedLibraryInternal)
    def staticLibraryBinary = Mock(StaticLibraryInternal)

    def "creates a matching visual studio project configuration for NativeBinary"() {
        when:
        visualStudioProjectMapper.mapToConfiguration(executableBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")
        executableBinary.source >> sources
        registry.addProjectConfiguration(executableBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.type == "Application"
        vsConfig.project.name == "vsProject"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "vsPlatform"
    }

    def "creates visual studio project and project configuration for SharedLibraryBinary"() {
        when:
        visualStudioProjectMapper.mapToConfiguration(sharedLibraryBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")
        sharedLibraryBinary.source >> sources
        registry.addProjectConfiguration(sharedLibraryBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(sharedLibraryBinary)
        vsConfig.type == "DynamicLibrary"
        vsConfig.project.name == "vsProject"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "vsPlatform"
    }

    def "creates visual studio project and project configuration for StaticLibraryBinary"() {
        when:
        visualStudioProjectMapper.mapToConfiguration(staticLibraryBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")
        staticLibraryBinary.source >> sources
        registry.addProjectConfiguration(staticLibraryBinary)

        and:
        registry.addProjectConfiguration(staticLibraryBinary)

        then:
        def vsConfig = registry.getProjectConfiguration(staticLibraryBinary)
        vsConfig.type == "StaticLibrary"
        vsConfig.project.name == "vsProject"
        vsConfig.configurationName == "vsConfig"
        vsConfig.platformName == "vsPlatform"
    }

    def "returns same visual studio project configuration for native binaries that share project name"() {
        when:
        visualStudioProjectMapper.mapToConfiguration(sharedLibraryBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "vsConfig", "vsPlatform")
        sharedLibraryBinary.source >> sources
        registry.addProjectConfiguration(sharedLibraryBinary)

        and:
        visualStudioProjectMapper.mapToConfiguration(staticLibraryBinary) >> new VisualStudioProjectMapper.ProjectConfigurationNames("vsProject", "other", "other")
        staticLibraryBinary.source >> sources
        registry.addProjectConfiguration(staticLibraryBinary)

        then:
        registry.getProjectConfiguration(sharedLibraryBinary).project == registry.getProjectConfiguration(staticLibraryBinary).project
    }

    interface ExecutableInternal extends ExecutableBinary, ProjectNativeBinaryInternal {}
    interface SharedLibraryInternal extends SharedLibraryBinary, ProjectNativeBinaryInternal {}
    interface StaticLibraryInternal extends StaticLibraryBinary, ProjectNativeBinaryInternal {}
}
