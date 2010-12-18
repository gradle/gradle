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

class MethodMetaDataTest extends Specification {
    final ClassMetaData owner = Mock()
    final MethodMetaData method = new MethodMetaData('method', owner)

    def formatsSignature() {
        method.returnType = new TypeMetaData('ReturnType')
        method.addParameter('param1', new TypeMetaData('ParamType'))
        method.addParameter('param2', new TypeMetaData('ParamType2'))

        expect:
        method.signature == 'ReturnType method(ParamType param1, ParamType2 param2)'
    }

    def formatsOverrideSignature() {
        method.returnType = new TypeMetaData('ReturnType')
        method.addParameter('param', new TypeMetaData('ParamType'))
        method.addParameter('param2', new TypeMetaData('ParamType2'))

        expect:
        method.overrideSignature == 'method(ParamType, ParamType2)'
    }
}
