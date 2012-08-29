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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.util.Matchers.strictlyEqual

/**
 * by Szczepan Faber, created at: 8/21/12
 */
class DefaultResolvedDependencyResultSpec extends Specification {

    def "object methods"() {
        def dependency = newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"))
        def same =   newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"))

        def differentRequested =  newDependency(newSelector("X", "b", "1"), newId("a", "b", "1"))
        def differentSelected =   newDependency(newSelector("a", "b", "1"), newId("a", "X", "1"))
        def differentFrom =       newDependency(newSelector("a", "b", "1"), newId("a", "b", "1"), "xxx")

        expect:
        dependency strictlyEqual(same)
        dependency != differentRequested
        dependency != differentSelected
        dependency != differentFrom

        dependency.hashCode() != differentRequested.hashCode()
        dependency.hashCode() != differentSelected.hashCode()
        dependency.hashCode() != differentFrom.hashCode()
    }

    private newDependency(ModuleVersionSelector requested, ModuleVersionIdentifier selected, String from = 'whatever') {
        new DefaultResolvedDependencyResult(requested, new DefaultResolvedModuleVersionResult(selected),
                new DefaultResolvedModuleVersionResult(newId("org", from, "1.0")))
    }
}
