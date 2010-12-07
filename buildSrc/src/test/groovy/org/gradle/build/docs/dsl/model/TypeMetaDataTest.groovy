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
        type.varargs = true

        expect:
        type.signature == 'org.gradle.SomeType[][]...'
    }
}
