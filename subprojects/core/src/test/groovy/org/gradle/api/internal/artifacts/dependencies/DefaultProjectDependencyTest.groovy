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

package org.gradle.api.internal.artifacts.dependencies

import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.DependencyResolveContext
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.component.ConfigurationNotConsumableException
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependencySpec.assertDeepCopy
import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.MatcherAssert.assertThat

class DefaultProjectDependencyTest extends AbstractProjectBuilderSpec {
    private projectDependency

    def setup() {
        projectDependency = new DefaultProjectDependency(project, null, false, TestFiles.taskDependencyFactory())
        project.version = "1.2"
        project.group = "org.gradle"
    }

    void "provides dependency information"() {
        expect:
        projectDependency.transitive
        projectDependency.name == project.name
        projectDependency.group == "org.gradle"
        projectDependency.version == "1.2"
    }

    void "transitive resolution resolves all dependencies"() {
        def context = Mock(DependencyResolveContext)

        def superConf = project.configurations.create("superConf")
        def conf = project.configurations.create("conf")
        conf.extendsFrom(superConf)

        def dep1 = Mock(ProjectDependency)
        def dep2 = Mock(ExternalDependency)
        conf.dependencies.add(dep1)
        superConf.dependencies.add(dep2)

        projectDependency = new DefaultProjectDependency(project, "conf", true, TestFiles.taskDependencyFactory())

        when:
        projectDependency.resolve(context)

        then:
        1 * context.transitive >> true
        1 * context.add(dep1)
        1 * context.add(dep2)
        0 * _
    }

    void "if resolution context is not transitive it will not contain all dependencies"() {
        def context = Mock(DependencyResolveContext)
        projectDependency = new DefaultProjectDependency(project, null, true, TestFiles.taskDependencyFactory())

        when:
        projectDependency.resolve(context)

        then:
        1 * context.transitive >> false
        0 * _
    }

    void "if dependency is not transitive the resolution context will not contain all dependencies"() {
        def context = Mock(DependencyResolveContext)
        projectDependency = new DefaultProjectDependency(project, null, true, TestFiles.taskDependencyFactory())
        projectDependency.setTransitive(false)

        when:
        projectDependency.resolve(context)

        then:
        0 * _
    }

    void "is buildable"() {
        def context = Mock(TaskDependencyResolveContext)

        def conf = project.configurations.create('conf')
        projectDependency = new DefaultProjectDependency(project, 'conf', true, TestFiles.taskDependencyFactory())

        when:
        projectDependency.buildDependencies.visitDependencies(context)

        then:
        1 * context.add(conf)
        1 * context.add({ it.is(conf.allArtifacts) })
        0 * _
    }

    void "doesn't allow selection of configuration is not consumable"() {
        def context = Mock(TaskDependencyResolveContext)

        project.configurations.create('conf') {
            canBeConsumed = false
        }
        projectDependency = new DefaultProjectDependency(project, 'conf', true, TestFiles.taskDependencyFactory())

        when:
        projectDependency.buildDependencies.visitDependencies(context)

        then:
        def e = thrown(ConfigurationNotConsumableException)
        e.message == "Selected configuration 'conf' on 'root project 'test-project'' but it can't be used as a project dependency because it isn't intended for consumption by other components."
    }

    void "does not build project dependencies if configured so"() {
        def context = Mock(TaskDependencyResolveContext)
        project.configurations.create('conf')
        projectDependency = new DefaultProjectDependency(project, 'conf', false, TestFiles.taskDependencyFactory())

        when:
        projectDependency.buildDependencies.visitDependencies(context)

        then:
        0 * _
    }

    void "is self resolving dependency"() {
        def conf = project.configurations.create('conf')
        projectDependency = new DefaultProjectDependency(project, 'conf', true, TestFiles.taskDependencyFactory())

        when:
        def files = projectDependency.resolve()

        then:
        0 * _

        and:
        files == conf.allArtifacts.files as Set
    }

    void "knows when content is equal"() {
        def d1 = createProjectDependency()
        def d2 = createProjectDependency()

        expect:
        d1.contentEquals(d2)
    }

    void "knows when content is not equal"() {
        def d1 = createProjectDependency()
        def d2 = createProjectDependency()
        d2.setTransitive(false)

        expect:
        !d1.contentEquals(d2)
    }

    void "can copy"() {
        def d1 = createProjectDependency()
        def copy = d1.copy()

        expect:
        assertDeepCopy(d1, copy)
        d1.dependencyProject == copy.dependencyProject
    }

    private createProjectDependency() {
        def out = new DefaultProjectDependency(project, true)
        out.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"))
        out
    }

    void "knows if is equal"() {
        expect:
        assertThat(new DefaultProjectDependency(project, true),
            strictlyEqual(new DefaultProjectDependency(project, true)))

        assertThat(new DefaultProjectDependency(project, "conf1", false, TestFiles.taskDependencyFactory()),
            strictlyEqual(new DefaultProjectDependency(project, "conf1", false, TestFiles.taskDependencyFactory())))

        when:
        def base = new DefaultProjectDependency(project, "conf1", true, TestFiles.taskDependencyFactory())
        def differentConf = new DefaultProjectDependency(project, "conf2", true, TestFiles.taskDependencyFactory())
        def differentBuildDeps = new DefaultProjectDependency(project, "conf1", false, TestFiles.taskDependencyFactory())
        def differentProject = new DefaultProjectDependency(Mock(ProjectInternal), "conf1", true, TestFiles.taskDependencyFactory())

        then:
        base != differentConf
        base != differentBuildDeps
        base != differentProject
    }
}
