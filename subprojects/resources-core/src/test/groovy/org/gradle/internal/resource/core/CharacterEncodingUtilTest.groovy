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

package org.gradle.internal.resource.core

import spock.lang.Specification

import static org.gradle.internal.resource.UriTextResource.extractCharacterEncoding

class CharacterEncodingUtilTest extends Specification {
    def extractsCharacterEncodingFromContentType() {
        expect:
        extractCharacterEncoding('content/unknown', null) == null
        extractCharacterEncoding('content/unknown', 'default') == 'default'
        extractCharacterEncoding(null, 'default') == 'default'
        extractCharacterEncoding('text/html', null) == null
        extractCharacterEncoding('text/html; charset=UTF-8', null) == 'UTF-8'
        extractCharacterEncoding('text/html; other=value; other="value"; charset=US-ASCII', null) == 'US-ASCII'
        extractCharacterEncoding('text/plain; other=value;', null) == null
        extractCharacterEncoding('text/plain; charset="charset"', null) == 'charset'
        extractCharacterEncoding('text/plain; charset="\\";\\="', null) == '";\\='
        extractCharacterEncoding('text/plain; charset=', null) == null
        extractCharacterEncoding('text/plain; charset; charset=;charset="missing-quote', null) == "missing-quote"
    }
}
