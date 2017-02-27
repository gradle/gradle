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

import org.gradle.api.Transformer
import org.gradle.api.artifacts.transform.ArtifactTransform
import spock.lang.Specification

class DefaultTransformedFileCacheTest extends Specification {
    def cache = new DefaultTransformedFileCache()

    def "reuses result for given file and transform"() {
        def transform = Mock(Transformer)

        when:
        def cachingTransform = cache.applyCaching(Transform1, ["abc"] as Object[], transform)
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

    def "does not reuse result when file path is different"() {
        def transform = Mock(Transformer)

        given:
        _ * transform.transform(new File("a")) >> [new File("a.1")]

        def cachingTransform = cache.applyCaching(Transform1, ["abc"] as Object[], transform)
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

    def "does not reuse result when implementation class is different"() {
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)

        given:
        _ * transform1.transform(new File("a")) >> [new File("a.1")]

        cache.applyCaching(Transform1, ["abc"] as Object[], transform1).transform(new File("a"))

        when:
        def result = cache.applyCaching(Transform2, ["abc"] as Object[], transform2).transform(new File("a"))

        then:
        result == [new File("a.2")]

        and:
        1 * transform2.transform(new File("a")) >> [new File("a.2")]
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.applyCaching(Transform1, ["abc"] as Object[], transform1).transform(new File("a"))
        def result3 = cache.applyCaching(Transform2, ["abc"] as Object[], transform2).transform(new File("a"))

        then:
        result2 == [new File("a.1")]
        result3 == [new File("a.2")]

        and:
        0 * transform1._
        0 * transform2._
    }

    def "does not reuse result when params are different"() {
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)

        given:
        _ * transform1.transform(new File("a")) >> [new File("a.1")]

        cache.applyCaching(Transform1, ["abc"] as Object[], transform1).transform(new File("a"))

        when:
        def result = cache.applyCaching(Transform1, ["def"] as Object[], transform2).transform(new File("a"))

        then:
        result == [new File("a.2")]

        and:
        1 * transform2.transform(new File("a")) >> [new File("a.2")]
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.applyCaching(Transform1, ["abc"] as Object[], transform1).transform(new File("a"))
        def result3 = cache.applyCaching(Transform1, ["def"] as Object[], transform2).transform(new File("a"))

        then:
        result2 == [new File("a.1")]
        result3 == [new File("a.2")]

        and:
        0 * transform1._
        0 * transform2._
    }

    static class Transform1 extends ArtifactTransform {
        @Override
        List<File> transform(File input) {
            throw new UnsupportedOperationException()
        }
    }

    static class Transform2 extends ArtifactTransform {
        @Override
        List<File> transform(File input) {
            throw new UnsupportedOperationException()
        }
    }
}
