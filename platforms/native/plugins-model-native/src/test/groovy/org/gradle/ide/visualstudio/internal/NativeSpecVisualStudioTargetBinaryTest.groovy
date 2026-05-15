/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.DomainObjectSet
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.plugins.ExtensionAware
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.PreprocessingTool
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.internal.DefaultFlavor
import org.gradle.nativeplatform.internal.DefaultFlavorContainer
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal
import org.gradle.nativeplatform.internal.SharedLibraryBinarySpecInternal
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

import static org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary.ProjectType.DLL
import static org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary.ProjectType.EXE
import static org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary.ProjectType.LIB
import static org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary.ProjectType.NONE

@UsesNativeServices
class NativeSpecVisualStudioTargetBinaryTest extends Specification {
    final flavor = new DefaultFlavor("flavor1")
    def flavors = new DefaultFlavorContainer(TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP)
    def exe = Mock(NativeExecutableSpec) {
        getFlavors() >> flavors
    }
    def lib = Mock(NativeLibrarySpec) {
        getFlavors() >> flavors
    }
    def platform = Mock(NativePlatform)
    def binaryNamingScheme = Mock(BinaryNamingScheme)
    def exeBinary = Mock(TestExecutableBinary) {
        getFlavor() >> flavor
        getComponent() >> exe
        getTargetPlatform() >> platform
        getNamingScheme() >> binaryNamingScheme
    }
    def sharedLibBinary = Mock(TestLibraryBinary) {
        getFlavor() >> flavor
        getComponent() >> lib
        getTargetPlatform() >> platform
        getNamingScheme() >> binaryNamingScheme
    }
    def staticLibBinary = Mock(TestStaticLibraryBinary) {
        getFlavor() >> flavor
        getComponent() >> lib
        getTargetPlatform() >> platform
        getNamingScheme() >> binaryNamingScheme
    }
    def targetBinary = new NativeSpecVisualStudioTargetBinary(exeBinary)
    def cppCompiler = Mock(PreprocessingTool)
    def cCompiler = Mock(PreprocessingTool)
    def rcCompiler = Mock(PreprocessingTool)

    def "tasks reflect binary tasks for executables"() {
        given:
        def tasks = Mock(NativeExecutableBinarySpec.TasksCollection)
        def lifecycleTask = Mock(InstallExecutable)
        when:
        exeBinary.tasks >> tasks
        tasks.withType(InstallExecutable) >> domainObjectSet(lifecycleTask)
        lifecycleTask.path >> "lifecycle-task-path"
        exe.projectPath >> ":project-path"

        then:
        targetBinary.buildTaskPath == "lifecycle-task-path"
        targetBinary.cleanTaskPath == ":project-path:clean"
    }

    def "tasks reflect binary tasks for libraries"() {
        given:
        targetBinary = new NativeSpecVisualStudioTargetBinary(sharedLibBinary)
        def tasks = Mock(SharedLibraryBinarySpec.TasksCollection)
        def lifecycleTask = Mock(Task)
        when:
        sharedLibBinary.tasks >> tasks
        tasks.build >> lifecycleTask
        lifecycleTask.path >> "lifecycle-task-path"
        lib.projectPath >> ":project-path"

        then:
        targetBinary.buildTaskPath == "lifecycle-task-path"
        targetBinary.cleanTaskPath == ":project-path:clean"
    }

    def "compiler defines are taken from cpp compiler configuration"() {
        when:
        cppCompiler.macros >> [foo: "bar", empty: null]
        exeBinary.getToolByName("cppCompiler") >> cppCompiler

        then:
        targetBinary.compilerDefines == ["foo=bar", "empty"]
    }

    def "compiler defines are taken from c compiler configuration"() {
        when:
        cCompiler.macros >> [foo: "bar", another: null]
        exeBinary.getToolByName("cCompiler") >> cCompiler

        then:
        targetBinary.compilerDefines == ["foo=bar", "another"]
    }

    def "resource defines are taken from rcCompiler config"() {
        when:
        rcCompiler.macros >> [foo: "bar", empty: null]
        exeBinary.getToolByName("rcCompiler") >> rcCompiler

        then:
        targetBinary.compilerDefines == ["foo=bar", "empty"]
    }

    def "compiler defines are taken from cpp, c and rc compiler configurations combined"() {
        when:
        cCompiler.macros >> [_c: null]
        cppCompiler.macros >> [foo: "bar", _cpp: null]
        rcCompiler.macros >> [rc: "defined", rc_empty: null]
        exeBinary.getToolByName('cCompiler') >> cCompiler
        exeBinary.getToolByName('cppCompiler') >> cppCompiler
        exeBinary.getToolByName('rcCompiler') >> rcCompiler

        then:
        targetBinary.compilerDefines == ["_c", "foo=bar", "_cpp", "rc=defined", "rc_empty"]
    }

    def "include paths include component headers"() {
        final inputs = new DefaultDomainObjectSet(LanguageSourceSet, CollectionCallbackActionDecorator.NOOP)

        when:
        exeBinary.inputs >> inputs
        exeBinary.libs >> []

        then:
        targetBinary.includePaths == [] as Set

        when:
        def file1 = new File("foo")
        def file2 = new File("bar")
        def file3 = new File("baz")
        def sourceSet = Mock(LanguageSourceSet)
        def sourceSet1 = headerSourceSet(file1, file2)
        def sourceSet2 = headerSourceSet(file3)
        inputs.addAll(sourceSet, sourceSet1, sourceSet2)

        and:
        exeBinary.inputs >> inputs
        exeBinary.libs >> []

        then:
        targetBinary.includePaths == [file1, file2, file3] as Set
    }

    def "include paths include library headers"() {
        when:
        def file1 = new File("foo")
        def file2 = new File("bar")
        def file3 = new File("baz")

        def deps1 = dependencySet(file1, file2)
        def deps2 = dependencySet(file3)

        exeBinary.inputs >> new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet, CollectionCallbackActionDecorator.NOOP)
        exeBinary.libs >> [deps1, deps2]

        then:
        targetBinary.includePaths == [file1, file2, file3] as Set
    }

    def "reflects source files of binary"() {
        def sourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet, CollectionCallbackActionDecorator.NOOP)
        def sourcefile1 = new File('file1')
        def sourcefile2 = new File('file2')
        def sourcefile3 = new File('file3')
        def resourcefile1 = new File('file4')
        def resourcefile2 = new File('file5')
        def resourcefile3 = new File('file6')
        def sourceSet = sourceSet(sourcefile1, sourcefile2, sourcefile3)
        def sourceSet3 = resourceSet(resourcefile1, resourcefile2, resourcefile3)
        sourceSets.addAll(sourceSet, sourceSet3)

        when:
        exeBinary.inputs >> sourceSets

        then:
        targetBinary.sourceFiles.files == [sourcefile1, sourcefile2, sourcefile3] as Set
        targetBinary.resourceFiles.files == [resourcefile1, resourcefile2, resourcefile3] as Set
    }

    def "produces correct project suffix"() {
        def binary = Mock(binaryType)
        targetBinary = new NativeSpecVisualStudioTargetBinary(binary)

        expect:
        targetBinary.getProjectType() == expectedSuffix

        where:
        binaryType                         | expectedSuffix
        SharedLibraryBinarySpecInternal    | DLL
        StaticLibraryBinarySpecInternal    | LIB
        NativeExecutableBinarySpecInternal | EXE
        NativeTestSuiteBinarySpecInternal  | EXE
        TestFooBinary                      | NONE
    }

    def "maps executable binary types to visual studio project"() {
        when:
        exe.getName() >> "exeName"
        exeBinary.getProjectPath() >> ":"
        binaryNamingScheme.getVariantDimensions() >> ["buildTypeOne"]

        then:
        checkNames targetBinary, "exeNameExe", 'buildTypeOne'
    }

    def "maps shared library binary types to visual studio projects"() {
        when:
        targetBinary = new NativeSpecVisualStudioTargetBinary(sharedLibBinary)
        lib.getName() >> "libName"
        sharedLibBinary.getProjectPath() >> ":"
        binaryNamingScheme.getVariantDimensions() >> ["buildTypeOne"]

        then:
        checkNames targetBinary, "libNameDll", 'buildTypeOne'
    }

    def "maps static library binary types to visual studio projects"() {
        when:
        targetBinary = new NativeSpecVisualStudioTargetBinary(staticLibBinary)
        lib.getName() >> "libName"
        staticLibBinary.getProjectPath() >> ":"
        binaryNamingScheme.getVariantDimensions() >> ["buildTypeOne"]

        then:
        checkNames targetBinary, "libNameLib", 'buildTypeOne'
    }

    def "includes project path in visual studio project name"() {
        when:
        exe.getName() >> "exeName"
        exeBinary.getProjectPath() >> ":subproject:name"
        binaryNamingScheme.getVariantDimensions() >> ["buildTypeOne"]

        then:
        checkNames targetBinary, "subproject_name_exeNameExe", 'buildTypeOne'
    }

    def "includes variant dimensions in configuration where component has multiple dimensions"() {
        when:
        exe.getName() >> "exeName"
        exeBinary.getProjectPath() >> ":"
        binaryNamingScheme.getVariantDimensions() >> ["platformOne", "buildTypeOne", "flavorOne"]

        then:
        checkNames targetBinary, "exeNameExe", 'platformOneBuildTypeOneFlavorOne'
    }

    private static checkNames(VisualStudioTargetBinary binary, def projectName, def configurationName) {
        assert binary.getVisualStudioProjectName() == projectName
        assert binary.getVisualStudioConfigurationName() == configurationName
        true
    }

    private HeaderExportingSourceSet headerSourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(HeaderExportingSourceSet)
        def sourceDirs = Mock(TestSourceDirectorySet)
        1 * sourceSet.exportedHeaders >> sourceDirs
        1 * sourceDirs.srcDirs >> allFiles
        return sourceSet
    }

    private LanguageSourceSet sourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(LanguageSourceSet)
        def sourceDirs = Mock(TestSourceDirectorySet)
        1 * sourceSet.source >> sourceDirs
        1 * sourceDirs.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, allFiles) }
        return sourceSet
    }

    private WindowsResourceSet resourceSet(File... files) {
        def allFiles = files as Set
        def sourceSet = Mock(WindowsResourceSet)
        def sourceDirs = Mock(TestSourceDirectorySet)
        1 * sourceSet.source >> sourceDirs
        1 * sourceDirs.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, allFiles) }
        return sourceSet
    }

    private NativeDependencySet dependencySet(File... files) {
        def deps = Mock(NativeDependencySet)
        def fileCollection = Mock(FileCollection)
        deps.includeRoots >> fileCollection
        fileCollection.files >> (files as Set)
        return deps
    }

    private DomainObjectSet<Task> domainObjectSet(Task... tasks) {
        def set = Stub(DomainObjectSet)
        set.iterator() >> tasks.iterator()
        return set
    }

    interface TestExecutableBinary extends NativeExecutableBinarySpecInternal, ExtensionAware {}

    interface TestLibraryBinary extends SharedLibraryBinarySpecInternal, ExtensionAware {}

    interface TestStaticLibraryBinary extends StaticLibraryBinarySpecInternal, ExtensionAware {}

    interface TestFooBinary extends NativeBinarySpecInternal {}

    interface TestSourceDirectorySet extends SourceDirectorySet, FileCollectionInternal {}
}
