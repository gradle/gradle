/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.tools.api.impl

import org.objectweb.asm.TypePath
import spock.lang.Specification

class AnnotationMemberTest extends Specification {

    def "comparison of two kinds gives the opposite answer in each direction"() {
        expect:
        Integer.signum(a <=> b) == -Integer.signum(b <=> a)
        (a <=> b) != 0

        where:
        a                                               | b
        annotation()                                    | parameterAnnotation(0)
        annotation()                                    | typeAnnotation(0, null)
        parameterAnnotation(0)                          | typeAnnotation(0, null)
    }

    def "a set keeps one member of each kind"() {
        given:
        def members = new TreeSet<AnnotationMember>()

        when:
        members.add(annotation())
        members.add(parameterAnnotation(0))
        members.add(typeAnnotation(0, null))

        then:
        members.size() == 3
    }

    def "a set keeps the type annotations of the same name on different targets"() {
        given:
        def members = new TreeSet<AnnotationMember>()

        when:
        members.add(typeAnnotation(0, null))
        members.add(typeAnnotation(1, null))
        members.add(typeAnnotation(1, TypePath.fromString('[')))

        then:
        members.size() == 3
    }

    def "a set keeps one type annotation per target"() {
        given:
        def members = new TreeSet<AnnotationMember>()

        when:
        members.add(typeAnnotation(0, null))
        members.add(typeAnnotation(0, null))

        then:
        members.size() == 1
    }

    def "a set keeps one parameter annotation per parameter"() {
        given:
        def members = new TreeSet<AnnotationMember>()

        when:
        members.add(parameterAnnotation(0))
        members.add(parameterAnnotation(1))
        members.add(parameterAnnotation(1))

        then:
        members.size() == 2
    }

    private static AnnotationMember annotation() {
        new AnnotationMember('LAnn;', true)
    }

    private static ParameterAnnotationMember parameterAnnotation(int parameter) {
        new ParameterAnnotationMember('LAnn;', true, parameter)
    }

    private static TypeAnnotationMember typeAnnotation(int typeRef, TypePath typePath) {
        new TypeAnnotationMember('LAnn;', true, typeRef, typePath)
    }
}
