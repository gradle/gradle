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

package org.gradle.internal

import spock.lang.Specification

import static org.gradle.internal.EitherTest.LeftValue.LEFT
import static org.gradle.internal.EitherTest.LeftValue.LEFT2
import static org.gradle.internal.EitherTest.RightValue.RIGHT
import static org.gradle.internal.EitherTest.RightValue.RIGHT2

class EitherTest extends Specification {

    enum LeftValue {
        LEFT, LEFT2
    }

    enum RightValue {
        RIGHT, RIGHT2
    }

    def left = either(LEFT)
    def right = either(RIGHT)

    def "equals() works"() {
        expect:
        either(LEFT) == either(LEFT)
        either(LEFT2) == either(LEFT2)
        either(LEFT) != either(LEFT2)
        either(LEFT2) != either(LEFT)
        either(LEFT) != either(RIGHT)

        either(RIGHT) == either(RIGHT)
        either(RIGHT2) == either(RIGHT2)
        either(RIGHT) != either(RIGHT2)
        either(RIGHT2) != either(RIGHT)
        either(RIGHT) != either(LEFT)
    }

    def "hashCode() works"() {
        expect:
        either(LEFT).hashCode() == either(LEFT).hashCode()

        either(RIGHT).hashCode() == either(RIGHT).hashCode()
    }

    def "toString() works"() {
        expect:
        left.toString() == "Left(LEFT)"

        right.toString() == "Right(RIGHT)"
    }

    def "if() works"() {
        expect:
        left.left.get() == LEFT
        !left.right.present

        !right.left.present
        right.right.get() == RIGHT
    }

    def "map() works"() {
        expect:
        left.mapLeft({ assert it == LEFT; LEFT2 }).left.get() == LEFT2
        left.mapRight({ assert false }).left.get() == LEFT

        right.mapRight({ assert it == RIGHT; RIGHT2 }).right.get() == RIGHT2
        right.mapLeft({ assert false }).right.get() == RIGHT
    }

    def "fold() subsumes flatMap()"() {
        expect:
        left.fold({ assert it == LEFT; either(LEFT2) }, { assert false }).left.get() == LEFT2

        right.fold({ assert false }, { assert it == RIGHT; either(RIGHT2) }).right.get() == RIGHT2
    }

    def "fold() works"() {
        expect:
        left.fold({ assert it == LEFT; LEFT2 }, { assert false }) == LEFT2

        right.fold({ assert false }, { assert it == RIGHT; RIGHT2 }) == RIGHT2
    }

    def "apply() works"() {
        def signal = new RuntimeException()

        when:
        left.apply({ throw signal }, { assert false })
        then:
        def leftEx = thrown RuntimeException
        leftEx == signal

        when:
        right.apply({ assert false }, { throw signal })
        then:
        def rightEx = thrown RuntimeException
        rightEx == signal
    }

    private static Either<LeftValue, RightValue> either(LeftValue value) {
        Either.left(value)
    }

    private static Either<LeftValue, RightValue> either(RightValue value) {
        Either.right(value)
    }
}
