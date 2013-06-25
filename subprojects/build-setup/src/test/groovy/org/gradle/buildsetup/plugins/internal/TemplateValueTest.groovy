/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import spock.lang.Specification

class TemplateValueTest extends Specification {
    def "escapes value for inclusion in a Groovy comment"() {
        expect:
        new TemplateValue(value).groovyComment == escaped

        where:
        value  | escaped
        ''     | ''
        'abc'  | 'abc'
        'a\n'  | 'a\n'
        'a\\b' | 'a\\\\b'
    }

    def "escapes value for inclusion in a Groovy string"() {
        expect:
        new TemplateValue(value).groovyString == escaped

        where:
        value  | escaped
        ''     | ''
        'abc'  | 'abc'
        'a\n'  | 'a\n'
        'a\\b' | 'a\\\\b'
        "'"    | "\\'"
        '"'    | '"'
        "\\'"  | "\\\\\\'"
    }
}
