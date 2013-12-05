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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.DependentSourceSet
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.nativebinaries.internal.NativeComponentInternal
import spock.lang.Specification

class DefaultVisualStudioProjectTest extends Specification {
    private DirectInstantiator instantiator = new DirectInstantiator()
    def component = Mock(NativeComponentInternal)
    def fileResolver = Mock(FileResolver)
    def projectResolver = Mock(VisualStudioProjectResolver)
    def vsProject = new DefaultVisualStudioProject("projectName", component, fileResolver, projectResolver, instantiator)

    def "names"() {
        final projectFile = new File("project")
        final filtersFile = new File("filters")
        when:
        fileResolver.resolve("visualStudio/projectName.vcxproj") >> projectFile
        fileResolver.resolve("visualStudio/projectName.vcxproj.filters") >> filtersFile

        then:
        vsProject.name == "projectName"
        vsProject.projectFile.location == projectFile
        vsProject.filtersFile.location == filtersFile
    }

    def "includes source files from all source sets"() {
        when:
        def file1 = Mock(File)
        def file2 = Mock(File)
        def file3 = Mock(File)
        def sourceSet1 = sourceSet(file1, file2)
        def sourceSet2 = sourceSet(file3)
        component.source >> new DefaultDomainObjectSet<LanguageSourceSet>(CppSourceSet, [sourceSet1, sourceSet2])

        then:
        vsProject.sourceFiles == [file1, file2, file3]
    }

    def "includes header files from all source sets"() {
        when:
        def file1 = Mock(File)
        def file2 = Mock(File)
        def file3 = Mock(File)
        def sourceSet1 = headerSourceSet(file1, file2)
        def sourceSet2 = headerSourceSet(file3)
        component.source >> new DefaultDomainObjectSet<LanguageSourceSet>(CppSourceSet, [sourceSet1, sourceSet2])

        then:
        vsProject.headerFiles == [file1, file2, file3]
    }

    def "has consistent uuid for same mapped component"() {
        when:
        def sameComponent = Mock(NativeComponentInternal)
        def otherComponent = Mock(NativeComponentInternal)

        def sameProject = new DefaultVisualStudioProject("projectName", component, fileResolver, projectResolver, instantiator)
        def samePath = new DefaultVisualStudioProject("projectName", sameComponent, fileResolver, projectResolver, instantiator)
        def differentPath = new DefaultVisualStudioProject("projectName", otherComponent, fileResolver, projectResolver, instantiator)
        def differentName = new DefaultVisualStudioProject("otherProject", component, fileResolver, projectResolver, instantiator)

        and:
        component.projectPath >> ":projectPath"
        sameComponent.projectPath >> ":projectPath"
        otherComponent.projectPath >> ":otherProjectPath"

        then:
        vsProject.uuid == sameProject.uuid
        vsProject.uuid == samePath.uuid
        vsProject.uuid != differentPath.uuid
        vsProject.uuid != differentName.uuid
    }

    private LanguageSourceSet sourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(LanguageSourceSet)
        def sourceDirs = Mock(SourceDirectorySet)
        1 * sourceSet.source >> sourceDirs
        1 * sourceDirs.files >> allFiles
        return sourceSet
    }

    private HeaderExportingSourceSet headerSourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(HeaderExportingSourceSet)
        def sourceDirs = Mock(SourceDirectorySet)
        1 * sourceSet.exportedHeaders >> sourceDirs
        1 * sourceDirs.files >> allFiles
        return sourceSet
    }

    private DependentSourceSet dependentSourceSet(Object... libs) {
        def sourceSet = Mock(DependentSourceSet)
        1 * sourceSet.libs >> libs
        return sourceSet
    }
}
