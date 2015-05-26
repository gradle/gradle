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
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

class DefaultModuleDependencySubstitutionTest extends Specification {
    def "can specify version to use"() {
        when:
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target == newComponentSelector("org", "foo", "1.0")
        !details.updated
        !details.selectionReason

        when:
        details.useVersion("1.0") //the same version

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target == newComponentSelector("org", "foo", "1.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE

        when:
        details.useVersion("2.0") //different version

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target != newComponentSelector("org", "foo", "1.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE

        details.target.version == "2.0"
        details.target.module == newComponentSelector("org", "foo", "1.0").module
        details.target.group == newComponentSelector("org", "foo", "1.0").group
    }

    def "can specify version with selection reason"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useVersion("1.0", VersionSelectionReasons.FORCED) //same version

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target == newComponentSelector("org", "foo", "1.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED

        when:
        details.useVersion("3.0", VersionSelectionReasons.FORCED) //different version

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target.version == "3.0"
        details.target.module == newComponentSelector("org", "foo", "1.0").module
        details.target.group == newComponentSelector("org", "foo", "1.0").group
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED
    }

    def "can override version and selection reason"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useVersion("2.0", VersionSelectionReasons.FORCED)
        details.useVersion("3.0", VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        details.requested == newComponentSelector("org", "foo", "1.0")
        details.target.version == "3.0"
        details.target.module == newComponentSelector("org", "foo", "1.0").module
        details.target.group == newComponentSelector("org", "foo", "1.0").group
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "does not allow null target"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useTarget(null)

        then:
        thrown(UnsupportedNotationException)

        when:
        details.useTarget(null, VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        thrown(UnsupportedNotationException)
    }

    def "does not allow null version"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useVersion(null)

        then:
        thrown(IllegalArgumentException)

        when:
        details.useVersion(null, VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        thrown(IllegalArgumentException)
    }

    def "can specify target module"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

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
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useTarget(project)

        then:
        _ * project.path >> ":bar"
        details.target instanceof ProjectComponentSelector
        details.target.projectPath == ":bar"
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "can mix configuring version and target module"() {
        def details = newModuleDependencySubstitution("org", "foo", "1.0")

        when:
        details.useVersion("1.5")

        then:
        details.target.toString() == 'org:foo:1.5'

        when:
        details.useTarget("com:bar:3.0")

        then:
        details.target.toString() == 'com:bar:3.0'

        when:
        details.useVersion('2.0')

        then:
        details.target.toString() == 'org:foo:2.0'
    }

    private static def newModuleDependencySubstitution(String group, String name, String version) {
        def dependencySubstitution = new DefaultDependencySubstitution(newComponentSelector(group, name, version), DefaultModuleVersionSelector.newSelector(group, name, version))
        return new DefaultModuleDependencySubstitution(dependencySubstitution)
    }

    private static ModuleComponentSelector newComponentSelector(String group, String module, String version) {
        return DefaultModuleComponentSelector.newSelector(group, module, version)
    }
}
