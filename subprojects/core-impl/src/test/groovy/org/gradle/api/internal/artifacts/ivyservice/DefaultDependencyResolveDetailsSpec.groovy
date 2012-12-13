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

package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons

/**
 * by Szczepan Faber, created at: 12/13/12
 */
class DefaultDependencyResolveDetailsSpec extends Specification {

    def "can specify version to use"() {
        def requested = newSelector("org", "foo", "1.0")

        when:
        def details = new DefaultDependencyResolveDetails(requested)

        then:
        details.requested == requested
        details.target == requested
        !details.updated
        !details.selectionReason

        when:
        details.useVersion("1.0") //the same version

        then:
        details.requested == requested
        details.target == requested
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_ACTION

        when:
        details.useVersion("2.0") //different version

        then:
        details.requested == requested
        details.target != requested
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_ACTION

        details.target.version == "2.0"
        details.target.name == requested.name
        details.target.group == requested.group
    }

    def "can specify version with selection reason"() {
        def requested = newSelector("org", "foo", "1.0")
        def details = new DefaultDependencyResolveDetails(requested)

        when:
        details.useVersion("1.0", VersionSelectionReasons.FORCED) //same version

        then:
        details.requested == requested
        details.target == requested
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED

        when:
        details.useVersion("3.0", VersionSelectionReasons.FORCED) //different version

        then:
        details.requested == requested
        details.target.version == "3.0"
        details.target.name == requested.name
        details.target.group == requested.group
        details.updated
        details.selectionReason == VersionSelectionReasons.FORCED
    }

    def "can override version and selection reason"() {
        def requested = newSelector("org", "foo", "1.0")
        def details = new DefaultDependencyResolveDetails(requested)

        when:
        details.useVersion("2.0", VersionSelectionReasons.FORCED)
        details.useVersion("3.0", VersionSelectionReasons.SELECTED_BY_ACTION)

        then:
        details.requested == requested
        details.target.version == "3.0"
        details.target.name == requested.name
        details.target.group == requested.group
        details.updated
        details.selectionReason == VersionSelectionReasons.SELECTED_BY_ACTION
    }

    def "does not allow null version"() {
        def details = new DefaultDependencyResolveDetails(newSelector("org", "foo", "1.0"))

        when:
        details.useVersion(null)

        then:
        thrown(IllegalArgumentException)

        when:
        details.useVersion(null, VersionSelectionReasons.SELECTED_BY_ACTION)

        then:
        thrown(IllegalArgumentException)
    }
}
