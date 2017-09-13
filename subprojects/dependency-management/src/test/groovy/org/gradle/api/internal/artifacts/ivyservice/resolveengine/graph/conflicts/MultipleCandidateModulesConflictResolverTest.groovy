/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.HasMultipleCandidateVersions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.internal.component.model.ComponentResolveMetadata
import spock.lang.Specification

class MultipleCandidateModulesConflictResolverTest extends Specification {
    final MultipleCandidateModulesConflictResolver resolver = MultipleCandidateModulesConflictResolver.create()

    final List<? extends ComponentResolutionState> candidates = []
    private ComponentResolutionState selected
    final ConflictResolverDetails details = Mock() {
        getCandidates() >> { candidates }
        getSelected() >> { selected }
        select(_) >> { selected = it[0] }
    }

    def "doesn't select a candidate if they are not of expected type"() {
        given:
        candidates << Mock(ComponentResolutionState)

        when:
        resolveConflicts()

        then:
        nothingSelected()
    }

    def "selects highest candidate in ranges"() {
        given:
        candidates << range(3, 6)
        candidates << range(4, 8)

        when:
        resolveConflicts()

        then:
        selected(6)
    }

    def "selects highest candidate when there are more than 2 ranges"() {
        given:
        candidates << range(1, 10)
        candidates << range(3, 6)
        candidates << range(3, 4)
        candidates << range(4, 9)

        when:
        resolveConflicts()

        then:
        selected(4)
    }

    def "doesn't selects when ranges are disjoint"() {
        given:
        candidates << range(3, 6)
        candidates << range(7, 8)

        when:
        resolveConflicts()

        then:
        nothingSelected()
    }

    private void selected(int version) {
        if (selected == null) {
            throw new AssertionError("Expected to select version $version but no version was selected")
        }
        String selectedVersion = selected.id.version
        assert selectedVersion == "$version": "Expected to select version $version but version $selectedVersion was selected"
    }

    private ComponentResolutionState range(int from, int to) {
        def candidate = Mock(ComponentResolutionState) {
            isResolved() >> true
            getMetaData() >> metadataWithRange(from, to)
            getId() >> Mock(ModuleVersionIdentifier) {
                getVersion() >> "$to"
            }
        }
        candidate
    }

    private MetadataWithRange metadataWithRange(int from, int to) {
        Mock(MetadataWithRange) {
            getAllVersions() >> (from..to).collect { "$it".toString() }.reverse()
            getSelected() >> Mock(ComponentResolveMetadata) {
                getId() >> Mock(ModuleVersionIdentifier) {
                    getVersion() >> "$to"
                }
            }
        }
    }

    private boolean nothingSelected() {
        selected == null
    }

    private void resolveConflicts() {
        resolver.select(details)
    }

    interface MetadataWithRange extends ComponentResolveMetadata, HasMultipleCandidateVersions {}
}
