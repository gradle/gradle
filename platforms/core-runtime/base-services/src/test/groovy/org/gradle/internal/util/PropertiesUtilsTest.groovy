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

package org.gradle.internal.util

import com.google.common.base.Charsets
import spock.lang.Specification

import java.nio.charset.Charset

class PropertiesUtilsTest extends Specification {
    def "empty properties are written properly"() {
        expect:
        write([:]) == ""
    }

    def "empty properties with comment are written properly"() {
        expect:
        write([:], "Line comment") == normalize("""
            #Line comment
            """)
    }

    def "simple properties are written sorted alphabetically"() {
        expect:
        write([one: "1", two: "2", three: "three"], "Line comment") == normalize("""
            #Line comment
            one=1
            three=three
            two=2
            """)
    }

    def "unicode characters are escaped when #description"() {
        expect:
        write([név: "Rezső"], "Eső leső", Charsets.ISO_8859_1) == normalize("""
            #Es\\u0151 les\\u0151
            n\\u00E9v=Rezs\\u0151
            """)
    }

    def "unicode characters are not escaped when encoding utf-8 encoding is used"() {
        expect:
        write([név: "Rezső"], "Eső leső", Charsets.UTF_8) == normalize("""
            #Es\\u0151 les\\u0151
            név=Rezső
            """)
    }

    def "specified line separator is used"() {
        expect:
        write([one: "1", two: "2", three: "three"], "Line comment", Charsets.ISO_8859_1, "EOL") == normalize("""
            #Line comment
            one=1
            three=three
            two=2
            """).split("\n", -1).join("EOL")
    }

    private static String write(Map<?, ?> properties, String comment = null, Charset charset = Charsets.ISO_8859_1, String lineSeparator = "\n") {
        def props = new Properties()
        props.putAll(properties)
        def data = new ByteArrayOutputStream()
        PropertiesUtils.store(props, data, comment, charset, lineSeparator)
        return new String(data.toByteArray(), charset)
    }

    private static String normalize(String text) {
        return text.stripIndent().trim() + "\n"
    }
}
