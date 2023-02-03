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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser

class LatestModuleConflictResolverTest extends AbstractConflictResolverTest {

    def setup() {
        resolver = new LatestModuleConflictResolver(new DefaultVersionComparator(), new VersionParser())
    }

    def "chooses latest module version #version for candidates #candidates"() {
        given:
        candidateVersions candidates

        when:
        resolveConflicts()

        then:
        selected version

        where:
        candidates                   | version
        ['1.0', '1.1']               | '1.1'
        ['1.1', '1.0']               | '1.1'
        ['1.1', '1.2', '1.0']        | '1.2'
        ['1.0', '1.0-beta-1']        | '1.0'
        ['1.0-beta-1', '1.0-beta-2'] | '1.0-beta-2'
    }

    def "can upgrade non strict version"() {
        given:
        prefer('1.0')
        strictly('1.1')

        when:
        resolveConflicts()

        then:
        selected '1.1'
    }

    def "can select a release version over unqualified"() {
        given:
        prefer('1.0-beta-1').release()
        prefer('1.0-beta-2')

        when:
        resolveConflicts()

        then:
        selected '1.0-beta-1'
    }

    def "can select qualified release over unqualified"() {
        given:
        prefer('2.11.4+4').release()
        prefer('2.11.4')

        when:
        resolveConflicts()

        then:
        selected '2.11.4+4'
    }

}
