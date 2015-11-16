/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import spock.lang.Specification

class DefaultDependencyResolveDetailsSpec extends Specification {

    def "can specify version to use"() {
        when:
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "1.0")
        !details.updated
        !details.selectionReason

        when:
        details.useVersion("1.0") //the same version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "1.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE

        when:
        details.useVersion("2.0") //different version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "2.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "can specify version with selection reason"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useVersion("1.0", VersionSelectionReasons.FORCED) //same version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "1.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED

        when:
        details.useVersion("3.0", VersionSelectionReasons.FORCED) //different version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "3.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED
    }

    def "can override version and selection reason"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useVersion("2.0", VersionSelectionReasons.FORCED)
        details.useVersion("3.0", VersionSelectionReasons.SELECTED_BY_RULE)

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "3.0")
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "does not allow null version"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

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
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useTarget("org:bar:2.0")

        then:
        details.target.toString() == 'org:bar:2.0'
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_RULE
    }

    def "can mix configuring version and target module"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useVersion("1.5")

        then:
        details.target.toString() == 'org:foo:1.5'

        when:
        details.useTarget("com:bar:3.0")

        then:
        details.target.toString() == 'com:bar:3.0'

        when:
        details.useVersion('5.0')

        then:
        details.target.toString() == 'com:bar:5.0'
    }

    private static def newDependencyResolveDetails(String group, String name, String version) {
        return new DefaultDependencyResolveDetails(new DefaultDependencySubstitution(newComponentSelector(group, name, version), newVersionSelector(group, name, version)))
    }

    private static ModuleComponentSelector newComponentSelector(String group, String module, String version) {
        return DefaultModuleComponentSelector.newSelector(group, module, version)
    }

    private static ModuleVersionSelector newVersionSelector(String group, String name, String version) {
        return DefaultModuleVersionSelector.newSelector(group, name, version)
    }
}
