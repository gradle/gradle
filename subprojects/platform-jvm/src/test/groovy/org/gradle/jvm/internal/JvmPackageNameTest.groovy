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

package org.gradle.jvm.internal

import spock.lang.Specification
import spock.lang.Unroll

class JvmPackageNameTest extends Specification {

    @Unroll
    def "should accept valid package name '#value'"() {
        expect:
        JvmPackageName.isValid(value)

        where:
        value << [
            '', 'c', 'com', 'com.p', 'com.example', 'com.example.p1', 'String.i3.αρετη.MAX_VALUE.isLetterOrDigit',
            'null_', 'true_', '_false', 'const_', '_public', 'com._private', 'int_.example', '$', 'a.$.b'
        ]
    }

    @Unroll
    def "should reject invalid package name '#value'"() {
        expect:
        !JvmPackageName.isValid(value)

        where:
        value << [
            ' ', '.', '..', 'com.', '.com', '1', 'com.1', '1com', 'com.example.p-1', 'com.example.1p', 'com/example/1p',
            'com.example.p 1', 'com..example.p1', 'null', 'true', 'false', 'const', 'public', 'com.private', '_', 'a._.b'
        ]
    }
}
