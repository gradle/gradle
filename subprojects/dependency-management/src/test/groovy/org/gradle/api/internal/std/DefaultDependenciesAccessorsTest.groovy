/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.std

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class DefaultDependenciesAccessorsTest extends Specification {
    private final static String PROJECTS_IDENTITY = 'da39a3ee5e6b4b0d3255bfef95601890afd80709'

    @Rule
    private final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def classpathRegistry = Mock(ClassPathRegistry)
    def workspace = Mock(DependenciesAccessorsWorkspace)
    def dependencyFactory = Mock(DefaultProjectDependencyFactory)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def scope = Mock(ClassLoaderScope)
    def settings = Stub(SettingsInternal) {
        getProjectRegistry() >> Stub(ProjectRegistry)
    }

    def builder = new DefaultDependenciesModelBuilder(TestUtil.objectFactory())

    @Subject
    DefaultDependenciesAccessors accessors = new DefaultDependenciesAccessors(
        classpathRegistry,
        workspace,
        dependencyFactory,
        buildOperationExecutor
    )

    def "generates accessors only if the model keys change"() {
        builder.alias("foo", "g:a:v")
        when:
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('a1af22ee922e33036e07736dcbe383acb2134a3d', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder.alias("foo", "g:a:v2")
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('a1af22ee922e33036e07736dcbe383acb2134a3d', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder.alias("foo", "other:a:v2")
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('a1af22ee922e33036e07736dcbe383acb2134a3d', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder.alias("bar", "changes:key:1.0")
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('839737519c42bb6246e5ca35781e55d9203d89b4', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder.bundle("myBundle", ["foo"])
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('f7c536cbfe187f080fbd7c8e3eb8107fe633c72c', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder.bundle("myBundle", ["bar"])
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('f7c536cbfe187f080fbd7c8e3eb8107fe633c72c', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _
    }

    def "accessors key is order-independent"() {
        builder.alias("foo", "g:a:v")
        builder.alias("bar", "g2:a2:v2")

        when:
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('839737519c42bb6246e5ca35781e55d9203d89b4', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _

        when:
        builder = new DefaultDependenciesModelBuilder(TestUtil.objectFactory())
        builder.alias("bar", "g2:a2:v2")
        builder.alias("foo", "g:a:v")

        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('839737519c42bb6246e5ca35781e55d9203d89b4', _)
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        0 * _
    }

    def "generates accessors if workspace is missing"() {
        def workspaceDir = tmpDir.createDir("id")
        builder.alias("foo", "g:a:v")

        when:
        accessors.generateAccessors(builder, scope, settings)

        then:
        1 * workspace.withWorkspace('a1af22ee922e33036e07736dcbe383acb2134a3d', _) >> { args ->
            args[1].executeInWorkspace(workspaceDir, Stub(ExecutionHistoryStore))
        }
        1 * workspace.withWorkspace(PROJECTS_IDENTITY, _)
        1 * buildOperationExecutor._
        1 * scope.export(DefaultClassPath.of(new File(workspaceDir, "classes")))
        0 * _

        and:
        accessors.sources.asFiles == [new File(workspaceDir, "sources")]
        accessors.classes.asFiles == [new File(workspaceDir, "classes")]
    }
}
