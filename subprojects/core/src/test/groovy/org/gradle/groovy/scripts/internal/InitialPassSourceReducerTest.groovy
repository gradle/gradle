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

package org.gradle.groovy.scripts.internal

import spock.lang.Specification

class InitialPassSourceReducerTest extends Specification {

    def "keeps leading script blocks and drops the rest"() {
        given:
        def script = """\
            import org.acme.Foo // a comment
            buildscript {
                repositories { mavenCentral() }
            }
            plugins {
                id 'java-library'
                id "org.acme.\${'x'}" version '1.0'
            }

            description = 'a library'
            dependencies {
                implementation project(':other')
            }
            """.stripIndent()

        expect:
        InitialPassSourceReducer.reduce(script) == """\
            import org.acme.Foo
            buildscript {
                repositories { mavenCentral() }
            }
            plugins {
                id 'java-library'
                id "org.acme.\${'x'}" version '1.0'
            }""".stripIndent()
    }

    def "preserves the position of blocks after comments"() {
        given:
        def script = "/* header\n   comment */ plugins { id 'java' }\nprintln 'hi'\n"

        expect:
        InitialPassSourceReducer.reduce(script) == "\n              plugins { id 'java' }"
    }

    def "result does not change when the rest of the script changes"() {
        given:
        def header = "plugins {\n    id 'java'\n}\n"

        expect:
        InitialPassSourceReducer.reduce(header + body1) == InitialPassSourceReducer.reduce(header + body2)

        where:
        body1                                | body2
        ""                                   | "description = 'x'\n"
        "println 'a'\n"                      | "println 'a much longer line'\n\n\n// with a comment\n"
        "tasks.register('a') { }\n"          | "plugins.withId('java') { println it }\n"
        "def x = 1 / 2\n"                    | "def s = \"\${project.name}\"\n"
    }

    def "scripts without script blocks reduce to nothing"() {
        expect:
        InitialPassSourceReducer.reduce(script) == ""

        where:
        script << [
            "",
            "println 'hello'\n",
            "import org.acme.Foo\nprintln Foo\n",
            "// just a comment\n",
            "apply plugin: 'java'\nplugins.withId('java') { }\n",
        ]
    }

    def "gives up when #description"() {
        expect:
        InitialPassSourceReducer.reduce(script) == null

        where:
        description                                    | script
        "a statement precedes a plugins block"         | "println 'x'\nplugins { id 'java' }\n"
        "a statement precedes a buildscript block"     | "ext.v = '1'\nbuildscript { }\n"
        "a block appears after other statements"       | "plugins { }\ndescription = 'x'\nbuildscript { }\n"
        "a block call uses parentheses"                | "plugins({ id 'java' })\n"
        "a block is followed by a method call"         | "buildscript { }.with { }\n"
        "a block is continued on the next line"        | "buildscript { }\n    .with { }\n"
        "two blocks are on the same line"              | "buildscript { } plugins { }\n"
        "an import follows other statements"           | "plugins { }\nprintln 'x'\nimport org.acme.Foo\n"
        "a package is declared"                        | "package org.acme\nplugins { }\n"
        "a slashy string might be present"             | "plugins { id(/java/) }\n"
        "a slashy string argument might be present"    | "plugins { }\nprintln /a{b/\n"
        "a dollar slashy string is present"            | "plugins { }\ndef x = \$/a/\$\n"
        "a string is unterminated"                     | "plugins { id 'java }\n"
        "a comment is unterminated"                    | "plugins { }\n/* comment\n"
        "braces do not match"                          | "plugins { id('java' }\n"
        "a block name is on its own line"              | "plugins\n{ }\n"
    }

    def "handles strings, interpolation and comments that contain braces"() {
        given:
        def script = """\
            plugins {
                id "a\${ [1, 2].collect { it } }" // }
                id '}'
                id '''}{'''
                /* } */
            }
            println 'x'
            """.stripIndent()

        expect:
        InitialPassSourceReducer.reduce(script) == """\
            plugins {
                id "a\${ [1, 2].collect { it } }" // }
                id '}'
                id '''}{'''
                /* } */
            }""".stripIndent()
    }

    def "keeps line endings and tabs"() {
        expect:
        InitialPassSourceReducer.reduce("// c\r\n\tplugins { }\r\nprintln 1\r\n") == "\r\n\tplugins { }"
    }
}
