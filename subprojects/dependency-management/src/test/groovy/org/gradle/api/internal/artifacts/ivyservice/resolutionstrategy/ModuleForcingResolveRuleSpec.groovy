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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class ModuleForcingResolveRuleSpec extends Specification {

    private static ModuleIdentifier mid(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }

    def "forces modules"() {
        given:
        def details = Mock(DependencySubstitutionInternal)

        when:
        new ModuleForcingResolveRule([
            newSelector(mid("org", "module1"), "1.0"),
            newSelector(mid("org", "module2"), "2.0"),
            //exotic module with colon in the name
            newSelector(mid("org", "module:with:colon"), "3.0"),
            newSelector(mid("org:with:colon", "module2"), "4.0")
        ]).execute(details)

        then:
        _ * details.requested >> DefaultModuleComponentSelector.newSelector(requested)
        _ * details.getOldRequested() >> requested
        1 * details.useTarget(DefaultModuleComponentSelector.newSelector(mid(requested.group, requested.name), forcedVersion), ComponentSelectionReasons.FORCED)
        0 * details._

        where:
        requested                                            | forcedVersion
        newSelector(mid("org", "module2"), "0.9")            | "2.0"
        newSelector(mid("org", "module2"), "2.1")            | "2.0"
        newSelector(mid("org", "module:with:colon"), "2.0")  | "3.0"
        newSelector(mid("org:with:colon", "module2"), "5.0") | "4.0"
    }

    def "does not force modules if they dont match"() {
        given:
        def details = Mock(DependencySubstitutionInternal)

        when:
        new ModuleForcingResolveRule([
            newSelector(mid("org", "module1"), "1.0"),
            newSelector(mid("org", "module2"), "2.0"),
            newSelector(mid("org", "module:with:colon"), "3.0"),
            newSelector(mid("org:with:colon", "module2"), "4.0")
        ]).execute(details)

        then:
        _ * details.getRequested() >> requested
        0 * details._

        where:
        requested << [
            newComponentSelector("orgX", "module2", "0.9"),
            newComponentSelector("org", "moduleX", "2.9"),
            newComponentSelector("orgX", "module:with:colon", "2.9"),
            newComponentSelector("org:with:colon", "moduleX", "2.9"),
            newComponentSelector("org:with", "colon:module2", "2.9"),
            newComponentSelector("org", "with:colon:module2", "2.9"),
        ]
    }

    def "does not force anything when input empty"() {
        def details = Mock(DependencySubstitutionInternal)

        when:
        new ModuleForcingResolveRule([]).execute(details)

        then:
        0 * details._
    }

    static newComponentSelector(String group, String name, String version) {
        DefaultModuleComponentSelector.newSelector(mid(group, name), version)
    }
}
