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
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.SELECTED_BY_RULE

class DefaultDependencyResolveDetailsSpec extends Specification {

    def "can specify version to use"() {
        when:
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "1.0")
        !details.delegate.updated
        details.delegate.ruleDescriptors == []

        when:
        details.useVersion("1.0") //the same version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "1.0")
        details.delegate.updated
        details.delegate.ruleDescriptors == [SELECTED_BY_RULE]

        when:
        details.useVersion("2.0") //different version

        then:
        details.requested == newVersionSelector("org", "foo", "1.0")
        details.target == newVersionSelector("org", "foo", "2.0")
        details.delegate.updated
        details.delegate.ruleDescriptors == [SELECTED_BY_RULE, SELECTED_BY_RULE]
    }

    def "does not allow null version"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useVersion(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "can specify target module"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useTarget("org:bar:2.0")

        then:
        details.target.toString() == 'org:bar:2.0'
        details.delegate.updated
        details.delegate.ruleDescriptors == [SELECTED_BY_RULE]
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

    def "can provide a custom selection reason with useTarget"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.because("forcefully upgrade dependency")
        details.useTarget("org:bar:2.0")

        then:
        details.target.toString() == 'org:bar:2.0'
        with (getReason(details)) {
            cause == ComponentSelectionCause.SELECTED_BY_RULE
            description == "forcefully upgrade dependency"
        }
    }

    def "can provide a custom selection reason with useVersion"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.because("forcefully upgrade dependency")
        details.useVersion("2.0")

        then:
        details.target.toString() == 'org:foo:2.0'
        with (getReason(details)) {
            cause == ComponentSelectionCause.SELECTED_BY_RULE
            description == "forcefully upgrade dependency"
        }
    }

    def "can provide a custom selection reason with useTarget before calling withDescription"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useTarget("org:bar:2.0")
        details.because("forcefully upgrade dependency")

        then:
        details.target.toString() == 'org:bar:2.0'
        with (getReason(details)) {
            cause == ComponentSelectionCause.SELECTED_BY_RULE
            description == "forcefully upgrade dependency"
        }
    }

    def "can provide a custom selection reason with useVersion before calling withDescription"() {
        def details = newDependencyResolveDetails("org", "foo", "1.0")

        when:
        details.useVersion("2.0")
        details.because("forcefully upgrade dependency")

        then:
        details.target.toString() == 'org:foo:2.0'
        with (getReason(details)) {
            cause == ComponentSelectionCause.SELECTED_BY_RULE
            description == "forcefully upgrade dependency"
        }
    }

    def "overwrites dependency reason"() {
        given:
        def details = newDependencyResolveDetails("org", "foo", "1.0", 'with a custom description')

        when:
        details.useVersion("2.0")
        details.because("forcefully upgrade dependency")

        then:
        details.target.toString() == 'org:foo:2.0'
        with (getReason(details)) {
            cause == ComponentSelectionCause.SELECTED_BY_RULE
            description == "forcefully upgrade dependency"
        }
    }

    private static def getReason(DefaultDependencyResolveDetails details) {
        assert details.delegate.updated
        assert details.delegate.ruleDescriptors.size() == 1
        return details.delegate.ruleDescriptors[0]
    }

    private static def newDependencyResolveDetails(String group, String name, String version, String reason = null, List<IvyArtifactName> artifacts = []) {
        return new DefaultDependencyResolveDetails(new DefaultDependencySubstitution(DependencyManagementTestUtil.componentSelectionDescriptorFactory(), newComponentSelector(group, name, version), artifacts), newVersionSelector(group, name, version))
    }

    private static ModuleComponentSelector newComponentSelector(String group, String module, String version) {
        def mid = DefaultModuleIdentifier.newId(group, module)
        return DefaultModuleComponentSelector.newSelector(mid, new DefaultImmutableVersionConstraint(version))
    }

    private static ModuleVersionSelector newVersionSelector(String group, String name, String version) {
        def mid = DefaultModuleIdentifier.newId(group, name)
        return DefaultModuleVersionSelector.newSelector(mid, version)
    }
}
