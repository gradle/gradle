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

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

class DefaultProjectDependencySubstitutionTest extends Specification {

    def "can override target and selection reason"() {
        def details = newProjectDependencySubstitution("foo")

        when:
        details.useTarget("org:foo:2.0", VersionSelectionReasons.FORCED)
        details.useTarget("org:foo:3.0", VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        details.requested == newComponentSelector(":foo")
        details.target.version == "3.0"
        details.target.module == newComponentSelector("org", "foo", "1.0").module
        details.target.group == newComponentSelector("org", "foo", "1.0").group
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "does not allow null target"() {
        def details = newProjectDependencySubstitution("foo")

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
        def details = newProjectDependencySubstitution("foo")

        when:
        details.useTarget("org:bar:2.0")

        then:
        details.target instanceof ModuleComponentSelector
        details.target.toString() == 'org:bar:2.0'
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "can specify target project"() {
        def project = Mock(Project)
        def details = newProjectDependencySubstitution("foo")

        when:
        details.useTarget(project)

        then:
        _ * project.path >> ":bar"
        details.target instanceof ProjectComponentSelector
        details.target.projectPath == ":bar"
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    private static def newProjectDependencySubstitution(String name) {
        def substitution = new DefaultDependencySubstitution(newComponentSelector(":" + name), DefaultModuleVersionSelector.newSelector("com.test", name, "1.0"))
        return new DefaultProjectDependencySubstitution(substitution)
    }

    private static ProjectComponentSelector newComponentSelector(String projectPath) {
        return DefaultProjectComponentSelector.newSelector(projectPath)
    }

    private static ModuleComponentSelector newComponentSelector(String group, String module, String version) {
        return DefaultModuleComponentSelector.newSelector(group, module, version)
    }
}
