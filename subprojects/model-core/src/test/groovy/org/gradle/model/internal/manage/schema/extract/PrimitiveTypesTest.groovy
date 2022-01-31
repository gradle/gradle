/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.model.internal.type.ModelType;
import spock.lang.Specification

class PrimitiveTypesTest extends Specification {

    def "#type is not primitive"() {
        expect:
        !PrimitiveTypes.isPrimitiveType(ModelType.of(type))

        where:
        type      | _
        Boolean   | _
        Character | _
        Byte      | _
        Short     | _
        Integer   | _
        Float     | _
        Long      | _
        Double    | _
    }

    def "#type is primitive"() {
        expect:
        PrimitiveTypes.isPrimitiveType(ModelType.of(type))

        where:
        type    | _
        boolean | _
        char    | _
        byte    | _
        short   | _
        int     | _
        float   | _
        long    | _
        double  | _
    }

    def "primitive #type default value is #value"() {
        expect:
        PrimitiveTypes.defaultValueOf(ModelType.of(type)) == value

        where:
        type | value
        boolean | false
        char    | '\u0000' as char
        byte    | 0 as byte
        short   | 0 as short
        int     | 0 as int
        float   | 0 as float
        long    | 0 as long
        double  | 0 as double
    }
}
