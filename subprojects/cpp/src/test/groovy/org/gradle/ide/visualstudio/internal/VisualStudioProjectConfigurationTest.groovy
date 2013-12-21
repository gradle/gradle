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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativebinaries.Executable
import org.gradle.nativebinaries.ExecutableBinary
import org.gradle.nativebinaries.NativeDependencySet
import org.gradle.nativebinaries.ProjectNativeBinary
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.internal.DefaultFlavorContainer
import org.gradle.nativebinaries.language.PreprocessingTool
import org.gradle.nativebinaries.platform.Platform
import spock.lang.Specification

class VisualStudioProjectConfigurationTest extends Specification {
    final flavor = new DefaultFlavor("flavor1")
    def flavors = new DefaultFlavorContainer(new DirectInstantiator())
    def exe = Mock(Executable) {
        getFlavors() >> flavors
    }
    def extensions = Mock(ExtensionContainer)
    def platform = Mock(Platform)
    def exeBinary = Mock(TestExecutableBinary) {
        getExtensions() >> extensions
        getFlavor() >> flavor
        getComponent() >> exe
        getTargetPlatform() >> platform
    }
    def configuration = new VisualStudioProjectConfiguration(null, "configName", "platformName" , exeBinary, "Application")
    def cppCompiler = Mock(PreprocessingTool)
    def cCompiler = Mock(PreprocessingTool)

    def "setup"() {
        flavors.add(flavor)
    }

    def "configuration has supplied names"() {
        expect:
        configuration.configurationName == "configName"
        configuration.platformName == "platformName"
        configuration.name == "configName|platformName"
    }

    def "configuration tasks are binary tasks"() {
        when:
        exeBinary.name >> "exeBinary"

        then:
        configuration.buildTask == "exeBinary"
        configuration.cleanTask == "cleanExeBinary"
    }

    def "compiler defines are taken from cpp compiler configuration"() {
        when:
        1 * extensions.findByName('cCompiler') >> null
        1 * extensions.findByName('cppCompiler') >> cppCompiler
        cppCompiler.macros >> [foo: "bar", empty: null]

        then:
        configuration.compilerDefines == ["foo=bar", "empty"]
    }

    def "compiler defines are taken from c compiler configuration"() {
        when:
        1 * extensions.findByName('cCompiler') >> cCompiler
        1 * extensions.findByName('cppCompiler') >> null
        cCompiler.macros >> [foo: "bar", another: null]

        then:
        configuration.compilerDefines == ["foo=bar", "another"]
    }

    def "compiler defines are taken from cpp and c compiler configurations combined"() {
        when:
        1 * extensions.findByName('cppCompiler') >> null
        1 * extensions.findByName('cCompiler') >> null

        then:
        configuration.compilerDefines == []

        when:
        1 * extensions.findByName('cCompiler') >> cCompiler
        1 * extensions.findByName('cppCompiler') >> cppCompiler
        cCompiler.macros >> [_c: null]
        cppCompiler.macros >> [foo: "bar", _cpp: null]

        then:
        configuration.compilerDefines == ["_c", "foo=bar", "_cpp"]
    }

    def "resource defines are taken from rcCompiler config"() {
        def rcCompiler = Mock(PreprocessingTool)

        when:
        1 * extensions.findByName('rcCompiler') >> null

        then:
        configuration.resourceDefines == []

        when:
        1 * extensions.findByName('rcCompiler') >> rcCompiler
        rcCompiler.macros >> [foo: "bar", empty: null]

        then:
        configuration.resourceDefines == ["foo=bar", "empty"]
    }

    def "include paths include component headers"() {
        final sources = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet)

        when:
        exeBinary.source >> sources
        exeBinary.libs >> []

        then:
        configuration.includePaths == []

        when:
        def file1 = Mock(File)
        def file2 = Mock(File)
        def file3 = Mock(File)
        def sourceSet = Mock(LanguageSourceSet)
        def sourceSet1 = headerSourceSet(file1, file2)
        def sourceSet2 = headerSourceSet(file3)
        sources.addAll(sourceSet, sourceSet1, sourceSet2)

        and:
        exeBinary.source >> sources
        exeBinary.libs >> []

        then:
        configuration.includePaths == [file1, file2, file3]
    }

    def "include paths include library headers"() {
        when:
        def file1 = Mock(File)
        def file2 = Mock(File)
        def file3 = Mock(File)

        def deps1 = dependencySet(file1, file2)
        def deps2 = dependencySet(file3)

        exeBinary.source >> new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet)
        exeBinary.libs >> [deps1, deps2]

        then:
        configuration.includePaths == [file1, file2, file3]
    }

    private HeaderExportingSourceSet headerSourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(HeaderExportingSourceSet)
        def sourceDirs = Mock(SourceDirectorySet)
        1 * sourceSet.exportedHeaders >> sourceDirs
        1 * sourceDirs.srcDirs >> allFiles
        return sourceSet
    }

    private NativeDependencySet dependencySet(File... files) {
        def deps = Mock(NativeDependencySet)
        def fileCollection = Mock(FileCollection)
        deps.includeRoots >> fileCollection
        fileCollection.files >> (files as Set)
        return deps
    }

    interface TestExecutableBinary extends ProjectNativeBinary, ExecutableBinary, ExtensionAware {}

}
