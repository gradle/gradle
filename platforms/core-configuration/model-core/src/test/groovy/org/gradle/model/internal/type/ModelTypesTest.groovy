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

package org.gradle.model.internal.type

import org.gradle.api.JavaVersion
import spock.lang.Specification

class ModelTypesTest extends Specification {
    def "collects type hierarchy of #types.simpleName to #closed.simpleName"() {
        expect:
        ModelTypes.collectHierarchy(types.collect { ModelType.of(it) }) == (closed.collect { ModelType.of(it) } as Set)

        where:
        types             | closed
        [Integer]         | typeHierarchyForInteger
        [Integer, Double] | typeHierarchyForInteger + typeHierarchyForDouble
        [Object]          | []
        [GroovyObject]    | []
    }

    static def getTypeHierarchyForDouble() {
        return maybeWithJava12ConstantTypes([Double, Number, Comparable, Serializable])
    }

    static def getTypeHierarchyForInteger() {
        return maybeWithJava12ConstantTypes([Integer, Number, Comparable, Serializable])
    }

    static def maybeWithJava12ConstantTypes(types) {
        if (JavaVersion.current().java12Compatible) {
            types += [Class.forName("java.lang.constant.Constable"), Class.forName("java.lang.constant.ConstantDesc")]
        }
        return types
    }
}
