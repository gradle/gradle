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

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.PreprocessingTool
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal
import org.gradle.nativeplatform.platform.NativePlatform
import spock.lang.Specification

class VisualStudioProjectConfigurationTest extends Specification {
    final flavor = new DefaultFlavor("flavor1")
    def flavors = new DefaultFlavorContainer(DirectInstantiator.INSTANCE)
    def exe = Mock(NativeExecutableSpec) {
        getFlavors() >> flavors
    }
    def extensions = Mock(ExtensionContainer)
    def platform = Mock(NativePlatform)
    def exeBinary = Mock(TestExecutableBinary) {
        getExtensions() >> extensions
        getFlavor() >> flavor
        getComponent() >> exe
        getTargetPlatform() >> platform
    }
    def configuration = new VisualStudioProjectConfiguration(null, "configName", "platformName", exeBinary)
    def cppCompiler = Mock(PreprocessingTool)
    def cCompiler = Mock(PreprocessingTool)
    def rcCompiler = Mock(PreprocessingTool)

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
        given:
        def tasks = Mock(NativeExecutableBinarySpec.TasksCollection)
        def lifecycleTask = Mock(Task)
        when:
        exeBinary.tasks >> tasks
        tasks.build >> lifecycleTask
        lifecycleTask.path >> "lifecycle-task-path"
        exe.projectPath >> ":project-path"

        then:
        configuration.buildTask == "lifecycle-task-path"
        configuration.cleanTask == ":project-path:clean"
    }

    def "compiler defines are taken from cpp compiler configuration"() {
        when:
        1 * extensions.findByName('cCompiler') >> null
        1 * extensions.findByName('cppCompiler') >> cppCompiler
        1 * extensions.findByName('rcCompiler') >> null
        cppCompiler.macros >> [foo: "bar", empty: null]

        then:
        configuration.compilerDefines == ["foo=bar", "empty"]
    }

    def "compiler defines are taken from c compiler configuration"() {
        when:
        1 * extensions.findByName('cCompiler') >> cCompiler
        1 * extensions.findByName('cppCompiler') >> null
        1 * extensions.findByName('rcCompiler') >> null
        cCompiler.macros >> [foo: "bar", another: null]

        then:
        configuration.compilerDefines == ["foo=bar", "another"]
    }

    def "resource defines are taken from rcCompiler config"() {
        when:
        1 * extensions.findByName('cCompiler') >> null
        1 * extensions.findByName('cppCompiler') >> null
        1 * extensions.findByName('rcCompiler') >> rcCompiler
        rcCompiler.macros >> [foo: "bar", empty: null]

        then:
        configuration.compilerDefines == ["foo=bar", "empty"]
    }

    def "compiler defines are taken from cpp, c and rc compiler configurations combined"() {
        when:
        1 * extensions.findByName('cppCompiler') >> null
        1 * extensions.findByName('cCompiler') >> null
        1 * extensions.findByName('rcCompiler') >> null

        then:
        configuration.compilerDefines == []

        when:
        1 * extensions.findByName('cCompiler') >> cCompiler
        1 * extensions.findByName('cppCompiler') >> cppCompiler
        1 * extensions.findByName('rcCompiler') >> rcCompiler
        cCompiler.macros >> [_c: null]
        cppCompiler.macros >> [foo: "bar", _cpp: null]
        rcCompiler.macros >> [rc: "defined", rc_empty: null]

        then:
        configuration.compilerDefines == ["_c", "foo=bar", "_cpp", "rc=defined", "rc_empty"]
    }

    def "include paths include component headers"() {
        final inputs = new DefaultDomainObjectSet(LanguageSourceSet)

        when:
        exeBinary.inputs >> inputs
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
        inputs.addAll(sourceSet, sourceSet1, sourceSet2)

        and:
        exeBinary.inputs >> inputs
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

        exeBinary.inputs >> new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet)
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

    interface TestExecutableBinary extends NativeExecutableBinarySpecInternal, ExtensionAware {}

}
