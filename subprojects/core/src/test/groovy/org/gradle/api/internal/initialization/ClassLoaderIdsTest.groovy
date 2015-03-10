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

package org.gradle.api.internal.initialization

import spock.lang.Specification

import static org.gradle.api.internal.initialization.ClassLoaderIds.*
import static org.gradle.util.Matchers.strictlyEquals

class ClassLoaderIdsTest extends Specification {

    def "equality"() {
        expect:
        strictlyEquals(first, same)
        first != different
        first != different2
        first.hashCode() != different.hashCode()
        first.hashCode() != different2.hashCode()

        where:
        first                  | same                   | different              | different2
        buildScript("x", "x")  | buildScript("x", "x")  | buildScript("y", "y")  | buildScript("x", "y")
        testTaskClasspath("x") | testTaskClasspath("x") | testTaskClasspath("y") | scopeNode("x")
        scopeNode("x")         | scopeNode("x")         | scopeNode("y")         | buildScript("x", "x")
    }
}
