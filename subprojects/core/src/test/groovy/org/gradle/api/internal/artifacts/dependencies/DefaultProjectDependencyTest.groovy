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

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Path

import static org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependencySpec.assertDeepCopy
import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.MatcherAssert.assertThat

class DefaultProjectDependencyTest extends AbstractProjectBuilderSpec {
    private ProjectDependency projectDependency

    def setup() {
        projectDependency = project.services.get(DependencyFactory).create(project)
        project.version = "1.2"
        project.group = "org.gradle"
    }

    def "exposes local project path"() {
        expect:
        projectDependency.path == project.path
    }

    void "provides dependency information"() {
        expect:
        projectDependency.transitive
        projectDependency.name == project.name
        projectDependency.group == "org.gradle"
        projectDependency.version == "1.2"
    }

    void "knows when content is equal"() {
        def d1 = createProjectDependency()
        def d2 = createProjectDependency()

        expect:
        d1 == d2
    }

    void "knows when content is not equal"() {
        def d1 = createProjectDependency()
        def d2 = createProjectDependency()
        d2.setTransitive(false)

        expect:
        d1 != d2
    }

    void "can copy"() {
        def d1 = createProjectDependency()
        def copy = d1.copy()

        expect:
        assertDeepCopy(d1, copy)
        d1.dependencyProject == copy.dependencyProject
    }

    def "requested capabilities exposes all capability selector types"() {
        when:
        projectDependency.capabilities {
            it.requireCapability('org:original:1')
            it.requireFeature('foo')
        }

        then:
        projectDependency.requestedCapabilities.size() == 2
        projectDependency.requestedCapabilities[0].group == 'org'
        projectDependency.requestedCapabilities[0].name == 'original'
        projectDependency.requestedCapabilities[0].version == '1'
        projectDependency.requestedCapabilities[1].group == 'org.gradle'
        projectDependency.requestedCapabilities[1].name == 'test-project-foo'
        projectDependency.requestedCapabilities[1].version == '1.2'
    }

    private createProjectDependency() {
        def out = new DefaultProjectDependency(project)
        out.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"))
        out
    }

    void "knows if is equal"() {
        def dep1 = new DefaultProjectDependency(project)
        def dep2 = new DefaultProjectDependency(project)

        def dep1WithConf = new DefaultProjectDependency(project)
        dep1WithConf.setTargetConfiguration("conf1")

        def dep2WithConf = new DefaultProjectDependency(project)
        dep2WithConf.setTargetConfiguration("conf1")

        expect:
        assertThat(dep1, strictlyEqual(dep2))
        assertThat(dep1WithConf, strictlyEqual(dep2WithConf))

        when:
        def base = new DefaultProjectDependency(project)
        base.setTargetConfiguration("conf1")

        def differentConf = new DefaultProjectDependency(project)
        differentConf.setTargetConfiguration("conf2")

        def otherProject = Mock(ProjectInternal) {
            getOwner() >> Mock(ProjectState) {
                getIdentity() >> new ProjectIdentity(Mock(BuildIdentifier), Path.path(":foo"), Path.path(":foo"), "foo")
            }
        }
        def differentProject = new DefaultProjectDependency(otherProject)
        differentProject.setTargetConfiguration("conf1")

        then:
        base != differentConf
        base != differentProject
    }
}
