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

package org.gradle.platform.base.internal

import org.gradle.util.TreeVisitor
import spock.lang.Specification

class CompositeBuildAbilityTest extends Specification {
    BinaryBuildAbility ability1 = Mock(BinaryBuildAbility)
    BinaryBuildAbility ability2 = Mock(BinaryBuildAbility)
    BinaryBuildAbility ability3 = Mock(BinaryBuildAbility)
    CompositeBuildAbility compositeAbility = new CompositeBuildAbility(ability1, ability2, ability3)

    def "is buildable when all abilities are buildable" () {
        when:
        ability1.isBuildable() >> true
        ability2.isBuildable() >> true
        ability3.isBuildable() >> true

        then:
        compositeAbility.isBuildable()
    }

    def "is not buildable when at least one ability is not buildable" () {
        when:
        ability1.isBuildable() >> true
        ability2.isBuildable() >> false
        0 * ability3.isBuildable()

        then:
        ! compositeAbility.isBuildable()
    }

    def "explains all reasons when multiple abilities are not buildable" () {
        TreeVisitor visitor = Mock(TreeVisitor)

        when:
        ability1.isBuildable() >> false
        ability1.explain(_) >> { TreeVisitor v -> v.node("ability1 is not buildable")}
        ability2.isBuildable() >> true
        0 * ability2.explain(_)
        ability3.isBuildable() >> false
        ability3.explain(_) >> { TreeVisitor v -> v.node("ability3 is not buildable")}
        compositeAbility.explain(visitor)

        then:
        visitor.node("ability1 is not buildable")
        visitor.node("ability3 is not buildable")
    }
}
