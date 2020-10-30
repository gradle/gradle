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

import com.google.common.collect.Interners
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultProjectDependencyFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class DefaultDependenciesAccessorsTest extends Specification {
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

    DefaultDependenciesModelBuilder builder
    FeaturePreviews featurePreviews = new FeaturePreviews()

    @Subject
    DefaultDependenciesAccessors accessors = new DefaultDependenciesAccessors(
        classpathRegistry,
        workspace,
        dependencyFactory,
        buildOperationExecutor,
        featurePreviews
    )

    def "generates accessors only if the model keys change"() {
        model {
            alias("foo", "g:a:v")
        }

        when:
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('6d80945bb0fc58a3bcc8b465b50ded192e8d884c', _)
        0 * _

        when:
        model {
            alias("foo", "g:a:v2")
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('6d80945bb0fc58a3bcc8b465b50ded192e8d884c', _)
        0 * _

        when:
        model {
            alias("foo", "other:a:v2")
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('6d80945bb0fc58a3bcc8b465b50ded192e8d884c', _)
        0 * _

        when:
        model {
            alias("bar", "changes:key:1.0")
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('8a3d01a1f4acf9f4daee71fe66771f1d3eb87eaf', _)
        0 * _

        when:
        model {
            alias("foo", "my:key:1.0")
            alias("bar", "my:key:1.0")
            bundle("myBundle", ["foo"])
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('ace9386fd246744074c8b00cd051303ff9f52582', _)
        0 * _

        when:
        model {
            alias("foo", "my:key:1.0")
            alias("bar", "my:key:1.0")
            bundle("myBundle", ["bar"])
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('ace9386fd246744074c8b00cd051303ff9f52582', _)
        0 * _
    }

    def "accessors key is order-independent"() {
        model {
            alias("foo", "g:a:v")
            alias("bar", "g2:a2:v2")
        }

        when:
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('07db55b1b1ded5e0912f2cd9e128aed46cbb9262', _)
        0 * _

        when:
        model {
            alias("bar", "g2:a2:v2")
            alias("foo", "g:a:v")
        }
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('07db55b1b1ded5e0912f2cd9e128aed46cbb9262', _)
        0 * _
    }

    def "generates accessors if workspace is missing"() {
        def workspaceDir = tmpDir.createDir("id")
        model {
            alias("foo", "g:a:v")
        }

        when:
        accessors.generateAccessors([builder], scope, settings)

        then:
        1 * workspace.withWorkspace('6d80945bb0fc58a3bcc8b465b50ded192e8d884c', _) >> { args ->
            args[1].executeInWorkspace(workspaceDir, Stub(ExecutionHistoryStore))
        }
        1 * buildOperationExecutor._
        1 * scope.export(DefaultClassPath.of(new File(workspaceDir, "classes")))
        0 * _

        and:
        accessors.sources.asFiles == [new File(workspaceDir, "sources")]
        accessors.classes.asFiles == [new File(workspaceDir, "classes")]
    }

    void model(@DelegatesTo(value = DefaultDependenciesModelBuilder, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        builder = new DefaultDependenciesModelBuilder("libs", Interners.newStrongInterner(), Interners.newStrongInterner(), TestUtil.objectFactory(), TestUtil.providerFactory(), Stub(PluginDependenciesSpec))
        spec.delegate = builder
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }
}
