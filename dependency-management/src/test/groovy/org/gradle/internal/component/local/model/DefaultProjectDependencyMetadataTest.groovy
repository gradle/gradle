/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.internal.component.model.DependencyMetadata
import spock.lang.Specification

class DefaultProjectDependencyMetadataTest extends Specification {
    def target = Stub(DependencyMetadata)
    def selector = Stub(ProjectComponentSelector)
    def dep = new DefaultProjectDependencyMetadata(selector, target)

    def "returns this when same target requested"() {
        expect:
        dep.withTarget(selector).is(dep)
    }

    def "delegates when different target requested"() {
        def requested = Stub(ComponentSelector)
        def delegateCopy = Stub(DependencyMetadata)

        given:
        target.withTarget(requested) >> delegateCopy

        expect:
        dep.withTarget(requested).is(delegateCopy)
    }
}
