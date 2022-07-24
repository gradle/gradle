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

package org.gradle.api.plugins.antlr.internal.antlr2

import spock.lang.Specification

class MetadataExtracterTest extends Specification {

    def "parses package information when defined in a separate line"() {
        given:
        def grammar = """
        header {
            package org.acme;
        }

        class TestGrammar extends Parser;

        options {
            buildAST = true;
        }

        expr:   mexpr (PLUS^ mexpr)* SEMI!
            ;

        mexpr
            :   atom (STAR^ atom)*
            ;

        atom:   INT
            ;"""
        expect:
        "org.acme" == new MetadataExtracter().getPackageName(new StringReader(grammar))
    }

    def "parses package information when header is declared as one-liner"() {
        given:
        def grammar = """
        header { package org.acme; }

        class TestGrammar extends Parser;

        options {
            buildAST = true;
        }

        expr:   mexpr (PLUS^ mexpr)* SEMI!
            ;

        mexpr
            :   atom (STAR^ atom)*
            ;

        atom:   INT
            ;"""
        expect:
        "org.acme" == new MetadataExtracter().getPackageName(new StringReader(grammar))
    }

    def "parses package information with header block in cpp syntax"() {
        given:
        def grammar = """
        header
{
package org.hibernate.hql.internal.antlr;

import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;

import org.hibernate.hql.internal.ast.ErrorReporter;
}

        class TestGrammar extends Parser;

        options {
            buildAST = true;
        }

        expr:   mexpr (PLUS^ mexpr)* SEMI!
            ;

        mexpr
            :   atom (STAR^ atom)*
            ;

        atom:   INT
            ;"""
        expect:
        "org.hibernate.hql.internal.antlr" == new MetadataExtracter().getPackageName(new StringReader(grammar))
    }
}
