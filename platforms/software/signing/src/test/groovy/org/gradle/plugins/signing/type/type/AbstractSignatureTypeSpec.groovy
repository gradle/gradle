/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.signing.type.type

import org.gradle.plugins.signing.type.AbstractSignatureType
import spock.lang.Specification

class AbstractSignatureTypeSpec extends Specification {

    static extension = "abc"
    def type = new AbstractSignatureType() { String getExtension() { AbstractSignatureTypeSpec.extension } }

    def "fileFor"() {
        when:
        def input = new File(path)

        then:
        type.fileFor(input) == new File(input.path + ".$extension")

        where:
        path << ["some.txt", "/absolute/some.txt", "relative/some.txt"]
    }

    def "combined extension"() {
        expect:
        type.combinedExtension(new File(name)) == expected

        where:
        name          | expected
        "pom.xml"     | "xml.$extension"
        "pom"         | extension
        "pom.xml.zip" | "zip.$extension"
        ""            | extension
    }


}
