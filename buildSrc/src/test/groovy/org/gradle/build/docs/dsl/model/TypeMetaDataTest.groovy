/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.model

import spock.lang.Specification

class TypeMetaDataTest extends Specification {
    final TypeMetaData type = new TypeMetaData('org.gradle.SomeType')

    def rawTypeForSimpleType() {
        expect:
        type.rawType.signature == 'org.gradle.SomeType'
    }

    def rawTypeForArrayType() {
        type.addArrayDimension()
        type.addArrayDimension()

        expect:
        type.rawType.signature == 'org.gradle.SomeType[][]'
    }

    def rawTypeForVarargsType() {
        type.setVarargs()

        expect:
        type.rawType.signature == 'org.gradle.SomeType...'
    }

    def rawTypeForParameterizedArrayType() {
        type.addArrayDimension()
        type.addArrayDimension()
        type.addTypeArg(new TypeMetaData('Type1'))

        expect:
        type.rawType.signature == 'org.gradle.SomeType[][]'
    }

    def rawTypeForParameterizedType() {
        type.addTypeArg(new TypeMetaData('Type1'))
        type.addTypeArg(new TypeMetaData('Type2'))

        expect:
        type.rawType.signature == 'org.gradle.SomeType'
    }

    def rawTypeForWildcardType() {
        type.setWildcard()

        expect:
        type.rawType.signature == 'java.lang.Object'
    }

    def rawTypeForWildcardWithUpperBound() {
        type.setUpperBounds(new TypeMetaData('OtherType'))

        expect:
        type.rawType.signature == 'OtherType'
    }

    def rawTypeForWildcardWithLowerBound() {
        type.setLowerBounds(new TypeMetaData('OtherType'))

        expect:
        type.rawType.signature == 'java.lang.Object'
    }

    def formatsSignature() {
        expect:
        type.signature == 'org.gradle.SomeType'
    }

    def formatsSignatureForArrayType() {
        type.addArrayDimension()
        type.addArrayDimension()

        expect:
        type.signature == 'org.gradle.SomeType[][]'
    }

    def formatsSignatureForArrayAndVarargsType() {
        type.addArrayDimension()
        type.addArrayDimension()
        type.setVarargs()

        expect:
        type.signature == 'org.gradle.SomeType[][]...'
    }

    def formatsSignatureForParameterizedType() {
        type.addTypeArg(new TypeMetaData('Type1'))
        type.addTypeArg(new TypeMetaData('Type2'))

        expect:
        type.signature == 'org.gradle.SomeType<Type1, Type2>'
    }

    def formatsSignatureForWildcardType() {
        type.setWildcard()

        expect:
        type.signature == '?'
    }

    def formatsSignatureForWildcardWithUpperBound() {
        type.setUpperBounds(new TypeMetaData('OtherType'))

        expect:
        type.signature == '? extends OtherType'
    }

    def formatsSignatureForWildcardWithLowerBound() {
        type.setLowerBounds(new TypeMetaData('OtherType'))

        expect:
        type.signature == '? super OtherType'
    }

    def visitsSignature() {
        TypeMetaData.SignatureVisitor visitor = Mock()

        when:
        type.visitSignature(visitor)

        then:
        1 * visitor.visitType('org.gradle.SomeType')
        0 * visitor._
    }

    def visitsSignatureForArrayType() {
        TypeMetaData.SignatureVisitor visitor = Mock()
        type.addArrayDimension()
        type.addArrayDimension()

        when:
        type.visitSignature(visitor)

        then:
        1 * visitor.visitType('org.gradle.SomeType')
        1 * visitor.visitText('[][]')
        0 * visitor._
    }

    def visitsSignatureForParameterizedType() {
        TypeMetaData.SignatureVisitor visitor = Mock()
        type.addTypeArg(new TypeMetaData('OtherType'))

        when:
        type.visitSignature(visitor)

        then:
        1 * visitor.visitType('org.gradle.SomeType')
        1 * visitor.visitText('<')
        1 * visitor.visitType('OtherType')
        1 * visitor.visitText('>')
        0 * visitor._
    }

    def visitsSignatureForWildcardType() {
        TypeMetaData.SignatureVisitor visitor = Mock()
        type.setWildcard()

        when:
        type.visitSignature(visitor)

        then:
        1 * visitor.visitText('?')
        0 * visitor._
    }
}
