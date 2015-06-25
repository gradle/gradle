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
import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.internal.AbstractBuildableModelElement
import org.gradle.api.internal.file.FileResolver
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.XmlConfigFile
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativeplatform.NativeBinary
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.util.CollectionUtils
/**
 * A VisualStudio project represents a set of binaries for a component that may vary in build type and target platform.
 */
class DefaultVisualStudioProject extends AbstractBuildableModelElement implements VisualStudioProject {
    private final String name
    private final DefaultConfigFile projectFile
    private final DefaultConfigFile filtersFile
    private final NativeComponentSpec component
    private final List<File> additionalFiles = []
    final Set<LanguageSourceSet> sources = new LinkedHashSet<LanguageSourceSet>()
    private final Map<NativeBinary, VisualStudioProjectConfiguration> configurations = [:]

    DefaultVisualStudioProject(String name, NativeComponentSpec component, FileResolver fileResolver, Instantiator instantiator) {
        this.name = name
        this.component = component
        projectFile = instantiator.newInstance(DefaultConfigFile, fileResolver, "${name}.vcxproj" as String)
        filtersFile = instantiator.newInstance(DefaultConfigFile, fileResolver, "${name}.vcxproj.filters" as String)
    }

    String getName() {
        return name
    }

    DefaultConfigFile getProjectFile() {
        return projectFile
    }

    DefaultConfigFile getFiltersFile() {
        return filtersFile
    }

    NativeComponentSpec getComponent() {
        return component
    }

    void addSourceFile(File sourceFile) {
        additionalFiles << sourceFile
    }

    String getUuid() {
        String projectPath = component.projectPath
        String vsComponentPath = "${projectPath}:${name}"
        return '{' + UUID.nameUUIDFromBytes(vsComponentPath.bytes).toString().toUpperCase() + '}'
    }

    void source(Collection<LanguageSourceSet> sources) {
        this.sources.addAll(sources)
        builtBy(sources)
    }

    List<File> getSourceFiles() {
        def allSource = [] as Set
        allSource.addAll additionalFiles
        sources.each { LanguageSourceSet sourceSet ->
            if (!(sourceSet instanceof WindowsResourceSet)) {
                allSource.addAll sourceSet.source.files
            }
        }
        return allSource as List
    }

    List<File> getResourceFiles() {
        def allResources = [] as Set
        sources.each { LanguageSourceSet sourceSet ->
            if (sourceSet instanceof WindowsResourceSet) {
                allResources.addAll sourceSet.source.files
            }
        }
        return allResources as List
    }

    List<File> getHeaderFiles() {
        def allHeaders = [] as Set
        sources.each { LanguageSourceSet sourceSet ->
            if (sourceSet instanceof HeaderExportingSourceSet) {
                allHeaders.addAll sourceSet.exportedHeaders.files
                allHeaders.addAll sourceSet.implicitHeaders.files
            }
        }
        return allHeaders as List
    }

    List<VisualStudioProjectConfiguration> getConfigurations() {
        return CollectionUtils.toList(configurations.values())
    }

    void addConfiguration(NativeBinarySpec nativeBinary, VisualStudioProjectConfiguration configuration) {
        configurations[nativeBinary] = configuration
        source nativeBinary.source
    }

    VisualStudioProjectConfiguration getConfiguration(NativeBinarySpec nativeBinary) {
        return configurations[nativeBinary]
    }

    public static class DefaultConfigFile implements XmlConfigFile {
        private final List<Action<? super XmlProvider>> actions = new ArrayList<Action<? super XmlProvider>>();
        private final FileResolver fileResolver
        private Object location

        DefaultConfigFile(FileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver
            this.location = defaultLocation
        }

        File getLocation() {
            return fileResolver.resolve(location)
        }

        void setLocation(Object location) {
            this.location = location
        }

        void withXml(Action<? super XmlProvider> action) {
            actions.add(action)
        }

        List<Action<? super XmlProvider>> getXmlActions() {
            return actions
        }
    }
}
