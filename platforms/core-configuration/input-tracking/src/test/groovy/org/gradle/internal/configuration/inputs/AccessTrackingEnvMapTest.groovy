/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.configuration.inputs

import com.google.common.collect.ForwardingMap

import javax.annotation.Nullable

class AccessTrackingEnvMapTest extends AbstractAccessTrackingMapTest {
    @Override
    protected Map<String, String> getMapUnderTestToRead() {
        return new AccessTrackingEnvMap(new StubEnvMap(), onAccess)
    }

    def "access to non-string element with containsKey throws"() {
        when:
        getMapUnderTestToRead().containsKey(Integer.valueOf(5))

        then:
        thrown(RuntimeException)
        0 * onAccess._
    }

    private class StubEnvMap extends ForwardingMap<String, String> {
        @Override
        String get(@Nullable Object key) {
            return super.get(Objects.requireNonNull(key))
        }

        @Override
        String getOrDefault(@Nullable Object key, String defaultValue) {
            return super.getOrDefault(Objects.requireNonNull(key), defaultValue)
        }

        @Override
        protected Map<String, String> delegate() {
            // Groovy ends up calling get() to obtain innerMap if there's no explicit qualifier, causing stack overflow.
            return AccessTrackingEnvMapTest.this.innerMap
        }
    }
}
