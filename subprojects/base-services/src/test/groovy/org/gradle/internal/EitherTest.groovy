/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal

import spock.lang.Specification

class EitherTest extends Specification {
    def value = new Object()

    def "left"() {
        expect:
        Either.left(value).getLeft() == value

        when:
        Either.right(value).left
        then:
        thrown NoSuchElementException
    }

    def "right"() {
        expect:
        Either.right(value).getRight() == value

        when:
        Either.left(value).right
        then:
        thrown NoSuchElementException
    }

    def "isLeft"() {
        expect:
        Either.left(value).isLeft()
        !Either.right(value).isLeft()
    }

    def "isRight"() {
        expect:
        !Either.left(value).isRight()
        Either.right(value).isRight()
    }

    def "get"() {
        expect:
        Either.left(value).get({ it == value }, { throw new RuntimeException() })
        Either.right(value).get({ throw new RuntimeException() }, { it == value })
    }

    def "apply"() {
        expect:
        Either.left(value).apply({ assert it == value }, { throw new RuntimeException() })
        Either.right(value).apply({ throw new RuntimeException() }, { assert it == value })
    }

    def "map"() {
        when:
        def leftMapped = Either.left(value).map({ it == value}, { throw new RuntimeException() })
        then:
        leftMapped.getLeft()
        !leftMapped.isRight()

        when:
        def rightMapped = Either.right(value).map({ throw new RuntimeException() }, { it == value})
        then:
        !rightMapped.isLeft()
        rightMapped.getRight()
    }
}
