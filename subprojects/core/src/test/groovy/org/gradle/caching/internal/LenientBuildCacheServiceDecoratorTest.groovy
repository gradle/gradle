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

import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheService

class LenientBuildCacheServiceDecoratorTest extends AbstractBuildCacheServiceDecoratorTest {
    def decorator = new LenientBuildCacheServiceDecorator(delegate)

    BuildCacheService getDecorator() {
        return decorator
    }

    List getExceptions() {
        [ new RuntimeException() ]
    }

    def "load returns false if the delegate throws BuildCacheException"() {
        delegate.load(key, reader) >> { throw new BuildCacheException() }
        expect:
        !decorator.load(key, reader)
    }

    def "store does not throw an exception if the delegate throws BuildCacheException"() {
        delegate.store(key, writer) >> { throw new BuildCacheException() }
        when:
        decorator.store(key, writer)
        then:
        noExceptionThrown()
    }

}
