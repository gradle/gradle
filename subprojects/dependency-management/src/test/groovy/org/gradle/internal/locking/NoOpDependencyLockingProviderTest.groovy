/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import spock.lang.Specification
import spock.lang.Subject

class NoOpDependencyLockingProviderTest extends Specification {

    @Subject
    def provider = NoOpDependencyLockingProvider.instance

    def 'does not find locked dependencies'() {
        when:
        def result = provider.findLockedDependencies('conf')

        then:
        result.isEmpty()
    }

    def 'does nothing on persist'() {
        given:
        def modules = Mock(Set)


        when:
        provider.persistResolvedDependencies('conf', modules)

        then:
        0 * _
    }
}
