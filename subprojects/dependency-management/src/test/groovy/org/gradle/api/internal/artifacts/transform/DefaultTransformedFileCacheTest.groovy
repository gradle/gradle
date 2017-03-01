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

package org.gradle.api.internal.artifacts.transform

import com.google.common.hash.HashCode
import org.gradle.api.Transformer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultTransformedFileCacheTest extends ConcurrentSpec {
    def cache = new DefaultTransformedFileCache()

    def "reuses result for given file and transform"() {
        def transform = Mock(Transformer)

        when:
        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        def result = cachingTransform.transform(new File("a"))

        then:
        result == [new File("a.1")]

        and:
        1 * transform.transform(new File("a")) >> [new File("a.1")]
        0 * transform._

        when:
        def result2 = cachingTransform.transform(new File("a"))

        then:
        result2 == [new File("a.1")]

        and:
        0 * transform._
    }

    def "applies transform once when requested concurrently by multiple threads"() {
        def transform = Mock(Transformer)

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result1 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result2 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result3 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result4 = cachingTransform.transform(new File("a"))
            }
        }

        then:
        result1 == [new File("a.1")]
        result2.is(result1)
        result3.is(result1)
        result4.is(result1)

        and:
        1 * transform.transform(new File("a")) >> [new File("a.1")]
        0 * transform._
    }

    def "multiple threads can transform files concurrently"() {
        when:
        def transform = cache.applyCaching(HashCode.fromInt(123)) { file ->
            instant."$file.name"
            thread.block()
            instant."${file.name}_done"
            [file]
        }
        async {
            start {
                transform.transform(new File("a"))
            }
            start {
                transform.transform(new File("b"))
            }
        }

        then:
        instant.a_done > instant.b
        instant.b_done > instant.a
    }

    def "does not reuse result when file path is different"() {
        def transform = Mock(Transformer)

        given:
        _ * transform.transform(new File("a")) >> [new File("a.1")]

        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        cachingTransform.transform(new File("a"))

        when:
        def result = cachingTransform.transform(new File("b"))

        then:
        result == [new File("b.1")]

        and:
        1 * transform.transform(new File("b")) >> [new File("b.1")]
        0 * transform._

        when:
        def result2 = cachingTransform.transform(new File("a"))
        def result3 = cachingTransform.transform(new File("b"))

        then:
        result2 == [new File("a.1")]
        result3 == [new File("b.1")]

        and:
        0 * transform._
    }

    def "does not reuse result when transform inputs are different"() {
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)

        given:
        _ * transform1.transform(new File("a")) >> [new File("a.1")]

        cache.applyCaching(HashCode.fromInt(123), transform1).transform(new File("a"))

        when:
        def result = cache.applyCaching(HashCode.fromInt(234), transform2).transform(new File("a"))

        then:
        result == [new File("a.2")]

        and:
        1 * transform2.transform(new File("a")) >> [new File("a.2")]
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.applyCaching(HashCode.fromInt(123), transform1).transform(new File("a"))
        def result3 = cache.applyCaching(HashCode.fromInt(234), transform2).transform(new File("a"))

        then:
        result2 == [new File("a.1")]
        result3 == [new File("a.2")]

        and:
        0 * transform1._
        0 * transform2._
    }
}
