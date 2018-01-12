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

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.BuildIdentity
import org.gradle.initialization.DefaultBuildIdentity
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

class DefaultDependencySubstitutionSpec extends Specification {
    def componentSelector = Mock(ComponentSelector)
    def details = new DefaultDependencySubstitution(componentSelector)

    def "can override target and selection reason for project"() {
        when:
        details.useTarget("org:foo:2.0", VersionSelectionReasons.FORCED)
        details.useTarget("org:foo:3.0", VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        details.requested == componentSelector
        details.target.group == "org"
        details.target.module == "foo"
        details.target.version == "3.0"
        details.updated
        details.selectionDescription == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "does not allow null target"() {
        when:
        details.useTarget(null)

        then:
        thrown(UnsupportedNotationException)

        when:
        details.useTarget(null, VersionSelectionReasons.SELECTED_BY_RULE)

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
        details.selectionDescription == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "can specify custom selection reason"() {
        when:
        details.useTarget("org:bar:2.0", 'with custom reason')

        then:
        details.target instanceof ModuleComponentSelector
        details.target.toString() == 'org:bar:2.0'
        details.updated
        details.selectionDescription == VersionSelectionReasons.SELECTED_BY_RULE.withReason('with custom reason')
    }

    def "can specify target project"() {
        def project = Mock(ProjectInternal)
        def services = new DefaultServiceRegistry()
        services.add(BuildIdentity, new DefaultBuildIdentity(new DefaultBuildIdentifier("TEST")))

        when:
        details.useTarget(project)

        then:
        _ * project.path >> ":bar"
        project.getServices() >> services
        details.target instanceof ProjectComponentSelector
        details.target.projectPath == ":bar"
        details.updated
        details.selectionDescription == VersionSelectionReasons.SELECTED_BY_RULE
    }
}
