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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import spock.lang.Specification

class NoBuildDependenciesArtifactSetTest extends Specification {
    def "returns original selected artifacts when they are empty"() {
        def target = Stub(ArtifactSet)
        def selector = Stub(ArtifactVariantSelector)

        when:
        target.select(_, _) >> ResolvedArtifactSet.EMPTY

        then:
        new NoBuildDependenciesArtifactSet(target).select(selector, Mock(ArtifactSelectionSpec)) == ResolvedArtifactSet.EMPTY
    }

    def "creates wrapper for non-empty set of selected artifacts"() {
        def target = Stub(ArtifactSet)
        def selector = Stub(ArtifactVariantSelector)
        def selected = Stub(ResolvedArtifactSet)
        def visitor = Mock(TaskDependencyResolveContext)

        given:
        target.select(_, _) >> selected

        when:
        def wrapper = new NoBuildDependenciesArtifactSet(target).select(selector, Mock(ArtifactSelectionSpec))
        wrapper.visitDependencies(visitor)

        then:
        0 * visitor._
    }
}
