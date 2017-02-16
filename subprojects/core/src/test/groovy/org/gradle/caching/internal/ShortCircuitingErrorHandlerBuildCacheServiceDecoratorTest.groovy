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

package org.gradle.caching.internal

import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheException

class ShortCircuitingErrorHandlerBuildCacheServiceDecoratorTest extends AbstractBuildCacheServiceDecoratorTest {
    def maxFailures = 2
    def decorator = new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(maxFailures, delegate)

    BuildCacheService getDecorator() {
        return decorator
    }

    def "stops calling through after defined number of read errors"() {
        when:
        (maxFailures+1).times {
            try {
                decorator.load(key, reader)
            } catch (Exception e) {
                // ignore
            }
        }
        decorator.store(key, writer)

        then:
        maxFailures * delegate.load(key, reader) >> { throw new BuildCacheException("Error") }
        _ * delegate.getDescription() >> "delegate"
        0 * _
    }

    def "stops calling through after defined number of write errors"() {
        when:
        (maxFailures+1).times {
            try {
                decorator.store(key, writer)
            } catch (Exception e) {
                // ignore
            }
        }
        decorator.load(key, reader)

        then:
        maxFailures * delegate.store(key, writer) >> { throw new BuildCacheException("Error") }
        _ * delegate.getDescription() >> "delegate"
        0 * _
    }
}
