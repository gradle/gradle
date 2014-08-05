/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.VersionSelection
import org.gradle.api.artifacts.VersionSelectionRules
import spock.lang.Specification

class DefaultVersionSelectionRulesTest extends Specification {
    def "all rules added get applied" () {
        def VersionSelectionRules versionSelectionRules = new DefaultVersionSelectionRules()
        def closure1 = Mock(Closure)
        def closure2 = Mock(Closure)
        def closure3 = Mock(Closure)

        when:
        versionSelectionRules.any closure1
        versionSelectionRules.any closure2
        versionSelectionRules.any closure3
        versionSelectionRules.apply(Stub(VersionSelection))

        then:
        1 * closure1.call(_)
        1 * closure2.call(_)
        1 * closure3.call(_)
    }
}
