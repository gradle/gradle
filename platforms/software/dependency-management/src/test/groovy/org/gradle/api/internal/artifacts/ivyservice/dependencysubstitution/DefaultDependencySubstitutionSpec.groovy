/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.FORCED
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.SELECTED_BY_RULE

class DefaultDependencySubstitutionSpec extends Specification {
    ComponentSelector componentSelector = Mock(ComponentSelector)
    List<IvyArtifactName> artifacts = []
    def details = newSubstitution()

    private DefaultDependencySubstitution newSubstitution() {
        new DefaultDependencySubstitution(DependencyManagementTestUtil.componentSelectionDescriptorFactory(), componentSelector, artifacts)
    }

    def "can override target and selection reason for project"() {
        when:
        details.useTarget("org:foo:2.0", FORCED)
        details.useTarget("org:foo:3.0", SELECTED_BY_RULE)

        then:
        details.requested == componentSelector
        details.target.group == "org"
        details.target.module == "foo"
        details.target.version == "3.0"
        details.updated
        details.ruleDescriptors == [FORCED, SELECTED_BY_RULE]
    }

    def "does not allow null target"() {
        when:
        details.useTarget(null)

        then:
        thrown(UnsupportedNotationException)

        when:
        details.useTarget(null, SELECTED_BY_RULE)

        then:
        thrown(UnsupportedNotationException)
    }

    def "can specify target module"() {
        when:
        details.useTarget("org:bar:2.0")

        then:
        details.target instanceof ModuleComponentSelector
        details.target.toString() == 'org:bar:2.0'
        details.updated
        details.ruleDescriptors == [SELECTED_BY_RULE]
    }

    def "can specify custom selection reason"() {
        when:
        details.useTarget("org:bar:2.0", 'with custom reason')

        then:
        details.target instanceof ModuleComponentSelector
        details.target.toString() == 'org:bar:2.0'
        details.updated
        details.ruleDescriptors.last().cause == ComponentSelectionCause.SELECTED_BY_RULE
        details.ruleDescriptors.last().description == 'with custom reason'
    }

    def "can specify target project"() {
        def projectState = Mock(ProjectState)
        projectState.identity >> new ProjectIdentity(Mock(BuildIdentifier), Path.path(":id:path"), Path.path(":bar"), "bar")
        def project = Mock(ProjectInternal)
        project.identityPath >> Path.path(":id:path")
        project.projectPath >> Path.path(":bar")
        project.name >> "bar"
        project.owner >> projectState

        when:
        details.useTarget(project)

        then:
        details.target instanceof ProjectComponentSelector
        details.target.projectPath == ":bar"
        details.updated
        details.ruleDescriptors == [SELECTED_BY_RULE]
    }

    def "can substitute with a different artifact"() {
        when:
        details.artifactSelection {
            it.selectArtifact(type, ext, classifier)
        }

        then:
        details.target == componentSelector
        details.updated
        details.artifactSelectionDetails.updated
        details.artifactSelectionDetails.targetSelectors.size() == 1
        details.artifactSelectionDetails.targetSelectors[0].type == type
        details.artifactSelectionDetails.targetSelectors[0].extension == ext
        details.artifactSelectionDetails.targetSelectors[0].classifier == classifier

        where:
        type  | ext   | classifier
        'jar' | 'jar' | 'classy'
        'zip' | 'zip' | null
        'jar' | 'zip' | 'classy'
    }

    def "artifact selection context has information about requested artifacts"() {
        def artifact = Stub(IvyArtifactName) {
            getName() >> 'foo'
            getExtension() >> 'jar'
            getType() >> 'type'
            getClassifier() >> 'classy'
        }

        def selectors = null

        when:
        details.artifactSelection {
            selectors = it.requestedSelectors
        }

        then:
        selectors == []

        when:
        artifacts << artifact
        details = newSubstitution()
        details.artifactSelection {
            assert it.hasSelectors()
            selectors = it.requestedSelectors
        }

        then:
        selectors.size() == artifacts.size()
        selectors[0].type == artifact.type
        selectors[0].extension == artifact.extension
        selectors[0].classifier == artifact.classifier
    }
}
