/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.descriptor.DefaultExclude
import spock.lang.Specification

/**
 * Tests {@link ModuleExclusions}.
 */
class ModuleExclusionsTest extends Specification {

    def moduleExclusions = new ModuleExclusions()

    def "excludes nothing when no exclude rules provided"() {
        when:
        def exclusions = moduleExclusions.excludeAny(ImmutableList.of())

        then:
        exclusions == moduleExclusions.nothing()
        exclusions.is(moduleExclusions.excludeAny(ImmutableList.of()))
    }

    def "can define multiple exclude rules"() {
        when:
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"))
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"))
        def ex = ImmutableList.of(exclude1, exclude2)
        def exclusions = moduleExclusions.excludeAny(ex)

        then:
        exclusions == moduleExclusions.excludeAny(ImmutableList.of(exclude1, exclude2))
        exclusions.is(moduleExclusions.excludeAny(ex))
    }

}
