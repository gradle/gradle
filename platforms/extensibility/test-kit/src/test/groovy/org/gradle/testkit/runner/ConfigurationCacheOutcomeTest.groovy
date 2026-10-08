/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testkit.runner

import spock.lang.Specification

class ConfigurationCacheOutcomeTest extends Specification {

    def "outcomes are equal when they are of the same kind"() {
        expect:
        ConfigurationCacheOutcome.stored() == ConfigurationCacheOutcome.stored()
        ConfigurationCacheOutcome.stored().hashCode() == ConfigurationCacheOutcome.stored().hashCode()
        ConfigurationCacheOutcome.stored() != ConfigurationCacheOutcome.reused()
    }

    def "outcome is described by its kind"() {
        expect:
        outcome.toString() == description

        where:
        outcome                                  | description
        ConfigurationCacheOutcome.notEnabled()   | "NotEnabled"
        ConfigurationCacheOutcome.stored()       | "Stored"
        ConfigurationCacheOutcome.reused()       | "Reused"
        ConfigurationCacheOutcome.storeSkipped() | "StoreSkipped"
        ConfigurationCacheOutcome.storeFailed()  | "StoreFailed"
        ConfigurationCacheOutcome.undetermined() | "Undetermined"
    }
}
