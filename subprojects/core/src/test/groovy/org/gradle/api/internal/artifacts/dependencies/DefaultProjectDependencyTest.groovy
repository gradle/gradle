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
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependencySpec.assertDeepCopy
import static org.gradle.util.Matchers.strictlyEqual
import static org.hamcrest.MatcherAssert.assertThat

class DefaultProjectDependencyTest extends Specification {

    ProjectState projectState = Stub(ProjectState) {
        getIdentity() >> ProjectIdentity.forRootProject(Path.ROOT, "test-project")
    }

    private ProjectDependency projectDependency

    def setup() {
        def project = Mock(ProjectInternal) {
            getGroup() >> "org.gradle"
            getVersion() >> "1.2"
            getOwner() >> projectState
        }
        projectState.getMutableModel() >> project

        projectDependency = new DefaultProjectDependency(projectState)
        projectDependency.setAttributesFactory(AttributeTestUtil.attributesFactory())
        projectDependency.setCapabilityNotationParser(new CapabilityNotationParserFactory(false).create())
        projectDependency.setObjectFactory(TestUtil.objectFactory())
    }

    def "exposes local project path"() {
        expect:
        projectDependency.path == projectState.identity.projectPath.asString()
    }

    void "provides dependency information"() {
        expect:
        projectDependency.transitive
        projectDependency.name == projectState.identity.projectName
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
        d1.path == copy.path
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
        def out = new DefaultProjectDependency(projectState)
        out.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"))
        out
    }

    void "knows if is equal"() {
        def dep1 = new DefaultProjectDependency(projectState)
        def dep2 = new DefaultProjectDependency(projectState)

        def dep1WithConf = new DefaultProjectDependency(projectState)
        dep1WithConf.setTargetConfiguration("conf1")

        def dep2WithConf = new DefaultProjectDependency(projectState)
        dep2WithConf.setTargetConfiguration("conf1")

        expect:
        assertThat(dep1, strictlyEqual(dep2))
        assertThat(dep1WithConf, strictlyEqual(dep2WithConf))

        when:
        def base = new DefaultProjectDependency(projectState)
        base.setTargetConfiguration("conf1")

        def differentConf = new DefaultProjectDependency(projectState)
        differentConf.setTargetConfiguration("conf2")

        def otherProjectState = Mock(ProjectState) {
            getIdentity() >> ProjectIdentity.forSubproject(Path.ROOT, Path.path(":bar"))
        }
        def differentProject = new DefaultProjectDependency(otherProjectState)
        differentProject.setTargetConfiguration("conf1")

        then:
        base != differentConf
        base != differentProject
    }
}
