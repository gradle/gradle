/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.problems.Problems
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.initialization.ProjectDescriptorInternal
import org.gradle.internal.build.BuildIdentity
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.operations.ProgressDisplayName
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39539")
class DefaultDependenciesAccessorsTest extends Specification {

    def buildOperationRunner = new TestBuildOperationRunner()
    def registry = new DefaultProjectDescriptorRegistry()

    def "shows the generation of the dependency accessors of the #build on a worker line"() {
        when:
        buildOperationRunner.run(DefaultDependenciesAccessors.dependencyAccessorsOperation(new BuildIdentity(buildPath), "libs", {}))

        then:
        def generate = buildOperationRunner.log.descriptors.last()
        generate.displayName == displayName
        generate.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Generating dependency accessors for 'libs'")

        where:
        build            | buildPath              | ownerToShow | displayName
        "root build"     | Path.ROOT              | null        | "Generate dependency accessors for 'libs'"
        "included build" | Path.path(":included") | ":included" | "Generate dependency accessors for 'libs' (:included)"
    }

    def "shows the generation of the project accessors of the #build on a worker line"() {
        when:
        buildOperationRunner.run(DefaultDependenciesAccessors.projectAccessorsOperation(new BuildIdentity(buildPath), {}))

        then:
        def generate = buildOperationRunner.log.descriptors.last()
        generate.displayName == displayName
        generate.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Generating project accessors")

        where:
        build            | buildPath              | ownerToShow | displayName
        "root build"     | Path.ROOT              | null        | "Generate project accessors"
        "included build" | Path.path(":included") | ":included" | "Generate project accessors (:included)"
    }

    @Issue("https://github.com/gradle/gradle/issues/22095")
    def "project accessors of #description are keyed on '#expected'"() {
        given:
        addProjects(rootName, paths)
        def visitor = Mock(InputVisitor)

        when:
        projectAccessorUnitOfWork().visitImmutableInputs(visitor)

        then:
        1 * visitor.visitInputProperty("projects", { it.value == expected })
        0 * _

        where:
        description                        | rootName | paths                     | expected
        "an empty registry"                | null     | []                        | ""
        "a root project"                   | "root"   | [":"]                     | "root,:"
        "a root project with another name" | "other"  | [":"]                     | "other,:"
        "subprojects in any order"         | "root"   | [":b", ":", ":a:c", ":a"] | "root,:,:a,:a:c,:b"
    }

    private UnitOfWork projectAccessorUnitOfWork() {
        def accessors = new DefaultDependenciesAccessors(
            Stub(ClassPathRegistry),
            Stub(DependenciesAccessorsWorkspaceProvider),
            Stub(DefaultProjectDependencyFactory),
            Stub(FeatureFlags),
            Stub(ExecutionEngine),
            Stub(FileCollectionFactory),
            Stub(InputFingerprinter),
            Stub(AttributesFactory),
            Stub(CapabilityNotationParser),
            Stub(Problems),
            buildOperationRunner
        )
        return new DefaultDependenciesAccessors.ProjectAccessorUnitOfWork(accessors, registry)
    }

    private void addProjects(String rootName, List<String> paths) {
        paths.toSorted().each {
            def path = Path.path(it)
            if (path == Path.ROOT) {
                projectDescriptor(rootName)
            } else {
                projectDescriptor(path.name, registry.getProject(path.parent.path))
            }
        }
    }

    private ProjectDescriptorInternal projectDescriptor(String name, ProjectDescriptorInternal parent = null) {
        return new DefaultProjectDescriptor(parent, name, new File(name), registry, TestFiles.resolver(), Stub(Problems))
    }
}
