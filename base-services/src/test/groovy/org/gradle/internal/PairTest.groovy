/*
 * Copyright 2014 the original author or authors.
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

import static Pair.unpackLeft
import static Pair.unpackRight

class PairTest extends Specification {

  def "can create and transform pair"() {
    given:
    def t = Pair.of(1, "a")

    expect:
    t.nestLeft(2).left == Pair.of(2, 1)
    t.nestLeft(2).right == "a"
    t.nestRight(2).left == 1
    t.nestRight(2).right == Pair.of(2, "a")

    t.pushLeft(2).left == 2
    t.pushLeft(2).right == t
    t.pushRight(2).left == t
    t.pushRight(2).right == 2

    t.mapLeft { it + 1 }.left == 2
    t.mapLeft { it + 1 }.right == "a"
    t.mapRight { it * 2 }.left == 1
    t.mapRight { it * 2 }.right == "aa"

    t.nestLeft(2).mapLeft(unpackLeft()).left == 2
    t.nestLeft(2).mapLeft(unpackRight()).left == 1
    t.nestLeft(2).mapLeft(unpackLeft()).right == "a"
    t.nestLeft(2).mapLeft(unpackRight()).right == "a"
    t.nestRight(2).mapRight(unpackLeft()).right == 2
    t.nestRight(2).mapRight(unpackRight()).right == "a"
    t.nestRight(2).mapRight(unpackLeft()).left == 1
    t.nestRight(2).mapRight(unpackRight()).left == 1
  }

}
