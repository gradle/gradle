/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.jvm.inspection

import spock.lang.Specification

import java.util.function.Predicate

class InvalidJvmInstallationCacheInvalidatorTest extends Specification {
    def 'closing triggers cache invalidation'() {
        def cache = Mock(ConditionalInvalidation<JvmInstallationMetadata>)

        given:
        def invalidator = new InvalidJvmInstallationCacheInvalidator(cache)

        when:
        invalidator.close()

        then:
        1 * cache.invalidateItemsMatching(_ as Predicate)
    }
}
