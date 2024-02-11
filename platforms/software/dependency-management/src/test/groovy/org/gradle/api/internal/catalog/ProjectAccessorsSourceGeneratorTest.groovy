/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.catalog

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.catalog.AbstractSourceGenerator.toJavaName

class ProjectAccessorsSourceGeneratorTest extends Specification {
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())
    private final ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))
    private final ClassPath classPath = classPathRegistry.getClassPath("DEPENDENCIES-EXTENSION-COMPILER")

    private final Map<String, GeneratedCode> generatedCode = [:]
    private final DefaultProjectDependencyFactory projectDependencyFactory = Stub(DefaultProjectDependencyFactory) {
        create(_) >> Stub(ProjectDependencyInternal)
    }
    private final ProjectFinder projectFinder = Stub(ProjectFinder) {
        getProject(_) >> Stub(ProjectInternal)
    }

    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "generates accessors for a single project"() {
        when:
        generateSources project("root")

        then:
        generatedCode['RootProjectAccessor'].with {
            hasSubprojectAccessors(":")
        }
        generatedCode['RootProjectDependency'].with {
            noSubAccessors()
        }

        and:
        compiles()
    }

    def "generates accessors for flat multiproject"() {
        when:
        generateSources project("root") {
            project("core")
            project("lib")
            project("utils")
        }

        then:
        generatedCode['RootProjectAccessor'].with {
            hasSubprojectAccessors ':core',
                ':lib',
                ':utils',
                ':'
        }
        ['CoreProjectDependency', 'UtilsProjectDependency', 'LibProjectDependency'].each {
            generatedCode[it].noSubAccessors()
        }

        and:
        compiles()
    }

    def "generates accessors for complex hierarchy"() {
        when:
        generateSources project("root") {
            project("utils") {
                project("json")
                project("xml")
            }
            project("core")
            project("lib") {
                project("sub") {
                    project("deep")
                }
            }
        }

        then:
        generatedCode['RootProjectAccessor'].with {
            hasSubprojectAccessors ':core',
                ':lib',
                ':utils',
                ':'
        }
        generatedCode['CoreProjectDependency'].with {
            noSubAccessors()
        }
        generatedCode['LibProjectDependency'].with {
            hasSubprojectAccessors(':lib:sub')
        }
        generatedCode['Lib_SubProjectDependency'].with {
            hasSubprojectAccessors(':lib:sub:deep')
        }
        generatedCode['Lib_Sub_DeepProjectDependency'].with {
            noSubAccessors()
        }
        generatedCode['UtilsProjectDependency'].with {
            hasSubprojectAccessors ':utils:json',
                ':utils:xml'
        }

        and:
        def accessor = compiles()
        accessor.root instanceof ProjectDependency
        accessor.core instanceof ProjectDependency
        accessor.utils instanceof ProjectDependency
        accessor.lib instanceof ProjectDependency
        accessor.lib.sub instanceof ProjectDependency
        accessor.lib.sub.deep instanceof ProjectDependency
        accessor.utils.json instanceof ProjectDependency
        accessor.utils.xml instanceof ProjectDependency
    }

    def "generates accessors for snake case project names"() {
        when:
        generateSources project("foo") {
            project("foo-core")
            project("foo-lib")
            project("foo-utils")
        }

        then:
        generatedCode['RootProjectAccessor'].with {
            hasSubprojectAccessors ':foo-core',
                ':foo-lib',
                ':foo-utils',
                ':'
        }
        ['FooCoreProjectDependency', 'FooUtilsProjectDependency', 'FooLibProjectDependency'].each {
            generatedCode[it].noSubAccessors()
        }

        and:
        def accessor = compiles()
        accessor.fooLib instanceof ProjectDependency
        accessor.fooUtils instanceof ProjectDependency
        accessor.fooUtils instanceof ProjectDependency
        accessor.foo.fooLib instanceof ProjectDependency
        accessor.foo.fooUtils instanceof ProjectDependency
    }

    ProjectDescriptor project(String name, @DelegatesTo(value = ProjectDescriptor, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def builder = new ProjectDescriptorBuilder(name, null)
        spec.delegate = builder
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        builder.build()
    }

    Object compiles() {
        def sources = generatedCode.values().collect {
            new TestClassSource(it.className, it.generatedCode)
        }
        def srcDir = tmpDir.createDir("src")
        def dstDir = tmpDir.createDir("dst")
        SimpleGeneratedJavaClassCompiler.compile(srcDir, dstDir, sources, classPath)
        def cl = new URLClassLoader([dstDir.toURI().toURL()] as URL[], this.class.classLoader)
        def factory = cl.loadClass("org.test.${RootProjectAccessorSourceGenerator.ROOT_PROJECT_ACCESSOR_CLASSNAME}")
        assert factory
        factory.newInstance(projectDependencyFactory, projectFinder)

    }

    private void generateSources(ProjectDescriptor descriptor) {
        def writer = new StringWriter()
        RootProjectAccessorSourceGenerator.generateSource(
            writer,
            descriptor,
            'org.test'
        )
        def code = new GeneratedCode(descriptor.name, writer.toString())
        generatedCode[code.className] = code
        generateSubAccessors(descriptor)
    }

    private void generateSubAccessors(ProjectDescriptor descriptor) {
        def writer = new StringWriter()
        ProjectAccessorsSourceGenerator.generateSource(
            writer,
            descriptor,
            'org.test'
        )
        def code = new GeneratedCode(descriptor.name, writer.toString())
        generatedCode[code.className] = code
        descriptor.children.each {
            generateSubAccessors(it)
        }
    }

    @CompileStatic
    private static class ProjectDescriptorBuilder {
        final String name
        final ProjectDescriptorBuilder parent
        final List<ProjectDescriptorBuilder> children = []

        ProjectDescriptorBuilder(String name, ProjectDescriptorBuilder parent) {
            this.name = name
            this.parent = parent
        }

        void project(String name, @DelegatesTo(value = ProjectDescriptor, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
            def childBuilder = new ProjectDescriptorBuilder(name, this)
            spec.delegate = childBuilder
            spec.resolveStrategy = Closure.DELEGATE_FIRST
            spec()
            children.add(childBuilder)
        }

        ProjectDescriptor build() {
            children.shuffle()
            def childDescriptors = children*.build() as Set<TestProjectDescriptor>
            def thisProject = new TestProjectDescriptor()
            thisProject.name = name
            thisProject.children = childDescriptors
            childDescriptors.each { it.parent = thisProject }
            thisProject
        }

    }

    @CompileStatic
    @ToString(
        includeNames = true,
        includes = ["name", "parent", "children"]
    )
    private static class TestProjectDescriptor implements ProjectDescriptor {
        String name
        ProjectDescriptor parent
        File projectDir
        String buildFileName
        File buildFile
        Set<TestProjectDescriptor> children

        @Override
        String getPath() {
            parent == null ? ':' :
                (parent.parent == null ? ":${name}" : "${parent.path}:${name}")
        }
    }

    @CompileStatic
    private class GeneratedCode {
        private final String name
        private final String generatedCode

        GeneratedCode(String name, String code) {
            this.name = name
            this.generatedCode = code
        }

        private int lineOfSubAccessor(String projectPath) {
            String baseName = AbstractProjectAccessorsSourceGenerator.toProjectName(projectPath == ':' ? toJavaName(name) : projectPath)
            String getterPath = baseName.contains('_') ? baseName.substring(baseName.lastIndexOf('_') + 1) : baseName
            int lineOfSubAccessor = lineOf("public ${baseName}ProjectDependency get${getterPath}() { return new ${baseName}ProjectDependency(getFactory(), create(\"${projectPath}\")); }")
            lineOfSubAccessor
        }

        void hasSubprojectAccessors(String... paths) {
            def expectedOrderOfAccessors = paths.sort()
            def lineNumberToAccessor = new TreeMap<Integer, String>()
            expectedOrderOfAccessors.each { path ->
                int lineOfSubAccessor = lineOfSubAccessor(path)
                assert lineOfSubAccessor > 0: "Could not find sub accessor for $path"
                lineNumberToAccessor[lineOfSubAccessor] = path
            }
            def accessorsInSourceOrder = lineNumberToAccessor.values() as List<String>
            assert expectedOrderOfAccessors == accessorsInSourceOrder
            assert expectedOrderOfAccessors.size() == subAccessorCount()
        }

        int subAccessorCount() {
            generatedCode.split("(\r)?\n").count {
                it.contains('public ') && it.contains('ProjectDependency get')
            } as int
        }

        void noSubAccessors() {
            assert subAccessorCount() == 0
        }

        private int lineOf(String lookup) {
            def lines = generatedCode.split("(\r)?\n")
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(lookup)) {
                    return i + 1;
                }
            }
            return -1;
        }

        private String getClassName() {
            def lines = generatedCode.split("(\r)?\n")
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.contains("public class ")) {
                    String tmp = line - "public class "
                    return tmp.substring(0, tmp.indexOf(' '))
                }
            }
            return null
        }

    }

    @Canonical
    static class TestClassSource implements ClassSource {

        final String className
        final String classSource

        @Override
        String getPackageName() {
            'org.test'
        }

        @Override
        String getSimpleClassName() {
            className
        }

        @Override
        String getSource() {
            classSource
        }
    }
}
