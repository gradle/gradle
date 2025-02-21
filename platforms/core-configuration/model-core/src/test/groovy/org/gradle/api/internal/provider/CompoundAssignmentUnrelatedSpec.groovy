/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider

import groovy.transform.CompileStatic
import spock.lang.Specification

/**
 * This test verifies that the source transformation doesn't break types that do not support the custom compound assignment protocol.
 */
@TransformCompoundAssignments
class CompoundAssignmentUnrelatedSpec extends Specification {
    class Origin {
        int intVal = 1
        List<String> listVal = []
    }

    def "compound assignment works for integer variables"() {
        given:
        int i = 1

        when:
        def v = (i += 2)

        then:
        v == 3
        i == 3
    }

    def "compound assignment works for integer properties"() {
        given:
        def origin = new Origin()

        when:
        def v = (origin.intVal += 2)

        then:
        v == 3
        origin.intVal == 3
    }

    def "compound assignment works for list variables"() {
        given:
        def list = ["a"]

        when:
        def v = (list += ["b"])

        then:
        v == ["a", "b"]
        list == ["a", "b"]
        v === list
    }

    def "compound assignment works for list properties"() {
        given:
        def origin = new Origin()

        when:
        def v = (origin.listVal += ["a"])

        then:
        v == ["a"]
        origin.listVal == ["a"]
        origin.listVal === v
    }

    @CompileStatic
    def intVarCompoundAssignment() {
        int i = 1
        def v = (i += 2)

        return [i, v]
    }

    def "compound assignment works for integer variables with static typing"() {
        given:
        def (i, v) = intVarCompoundAssignment()

        expect:
        v == 3
        i == 3
    }

    @CompileStatic
    def intPropCompoundAssignment() {
        def origin = new Origin()
        def v = (origin.intVal += 2)
        return [origin.intVal, v]
    }

    def "compound assignment works for integer properties with static typing"() {
        given:
        def (i, v) = intPropCompoundAssignment()

        expect:
        v == 3
        i == 3
    }

    @CompileStatic
    def listVarCompoundAssignment() {
        def list = ["a"]
        def v = (list += ["b"])

        return [list, v]
    }

    def "compound assignment works for list variables with static typing"() {
        given:
        def (i, v) = listVarCompoundAssignment()

        expect:
        v == ["a", "b"]
        i == ["a", "b"]
        v === i
    }

    @CompileStatic
    def listPropCompoundAssignment() {
        def origin = new Origin()
        def v = (origin.listVal += ["a"])
        return [origin.listVal, v]
    }

    def "compound assignment works for list properties with static typing"() {
        given:
        def (i, v) = listPropCompoundAssignment()

        expect:
        v == ["a"]
        i == ["a"]
        v === i
    }
}
