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

package org.gradle.api.internal.artifacts.result

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.util.Matchers.strictlyEqual

/**
 * Created: 10/08/2012
 * @author Szczepan Faber
 */
class DefaultResolvedModuleVersionResultSpec extends Specification {

    def "equals"() {
        def module = newModule("group", "module", "version")
        def same = newModule("group", "module", "version")

        def differentGroup = newModule("other", "module", "version")
        def differentModule = newModule("group", "other", "version")
        def differentVersion = newModule("group", "module", "other")

        expect:
        module strictlyEqual(same)
        module != differentGroup
        module != differentModule
        module != differentVersion
    }

    def "equals does not consider dependencies"() {
        def result = newModule("group", "module", "version")
        def differentDeps = newModule("group", "module", "version")
                .addDependency(newDependency())

        expect:
        result == differentDeps
    }

    def "equals and hashcode do not recurse forever"() {
        def module = newModule("a", "c", "2")

        def dependency = newDependency("a", "c", "2")
        def selectedModule = dependency.selected
        selectedModule.addDependency(dependency)

        expect:
        selectedModule.hashCode()
        selectedModule.equals(module)
    }

    def newDependency(String group='a', String module='a', String version='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), newId(group, module, version), [])
    }

    def newModule(String group, String module, String version) {
        new DefaultResolvedModuleVersionResult(newId(group, module, version))
    }
}
