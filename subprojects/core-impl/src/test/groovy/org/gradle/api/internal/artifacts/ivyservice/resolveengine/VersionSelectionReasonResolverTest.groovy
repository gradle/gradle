/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import spock.lang.Specification
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons

/**
 * by Szczepan Faber, created at: 1/29/13
 */
class VersionSelectionReasonResolverTest extends Specification {

    def "configures selection reason"() {
        def delegate = Mock(ModuleConflictResolver)
        VersionSelectionReasonResolver resolver = new VersionSelectionReasonResolver(delegate)

        def root = Mock(ModuleRevisionResolveState)
        def candidate1 = Mock(ModuleRevisionResolveState)
        def candidate2 = Mock(ModuleRevisionResolveState)

        when:
        def out = resolver.select([candidate1, candidate2], root)

        then:
        out == candidate2

        and:
        1 * delegate.select([candidate1, candidate2], root) >> candidate2
        1 * candidate2.getSelectionReason() >> VersionSelectionReasons.REQUESTED
        1 * candidate2.setSelectionReason(VersionSelectionReasons.CONFLICT_RESOLUTION)
        0 * _._
    }
}
