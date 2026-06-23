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

package org.gradle.features.internal

import org.gradle.features.internal.builders.Language
import spock.lang.Specification

class LanguageTest extends Specification {

    def "propertyAccessor formats as Java getter or Kotlin property"() {
        expect:
        language.propertyAccessor("obj", "foo") == expected

        where:
        language        | expected
        Language.JAVA   | "obj.getFoo()"
        Language.KOTLIN | "obj.foo"
    }

    def "statementEnd terminates Java statements with semicolon and Kotlin with empty"() {
        expect:
        language.statementEnd() == expected

        where:
        language        | expected
        Language.JAVA   | ";"
        Language.KOTLIN | ""
    }

    def "printCall renders the language-specific stdout call"() {
        expect:
        language.printCall("\"hello\"") == expected

        where:
        language        | expected
        Language.JAVA   | "System.out.println(\"hello\");"
        Language.KOTLIN | "println(\"hello\")"
    }

    def "asFileExpression renders the language-specific provider-to-path tail"() {
        expect:
        language.asFileExpression() == expected

        where:
        language        | expected
        Language.JAVA   | ".getAsFile().getAbsolutePath()"
        Language.KOTLIN | ".asFile.absolutePath"
    }

    def "printStatement composes objectType, propertyName, and value into a print"() {
        expect:
        language.printStatement("definition", "foo", "obj.getFoo()") == expected

        where:
        language        | expected
        Language.JAVA   | "System.out.println(\"definition foo = \" + obj.getFoo());"
        Language.KOTLIN | "println(\"definition foo = \" + obj.getFoo())"
    }
}
