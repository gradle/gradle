/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.antlr



import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.hamcrest.Matcher
import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith
/**
 * @author Strong Liu <stliu@hibernate.org>
 */
class Antlr3PluginIntegrationTests extends WellBehavedPluginTest {

    def setup() {
        writeBuildFile()
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds('compileJava')
        file("build/generated-src/antlr/main/org/gradle/api/plugins/antlr/ExprLexer.java").exists()
        file("build/generated-src/antlr/main/org/gradle/api/plugins/antlr/ExprParser.java").exists()
        file("build/generated-src/antlr/main/Expr.tokens").exists()
    }



    private goodCode() {
        file('src/main/antlr/org/gradle/api/plugins/antlr/Expr.g') << """
        grammar Expr;

@header {
\tpackage org.gradle.api.plugins.antlr;
\timport java.util.Map;
\timport java.util.HashMap;
}
@lexer::header {
\tpackage org.gradle.api.plugins.antlr;
}

@members {
\tMap<String, Integer> memory = new HashMap<String, Integer>();
}
prog : \tstat+;
stat :  expr NEWLINE {System.out.println(\$expr.value);}
\t|\tID '=' expr NEWLINE
\t\t{memory.put(\$ID.text, Integer.valueOf(\$expr.value));}
\t|\tNEWLINE
\t;
\t
expr returns [int value]
\t:\te=multExpr{\$value = \$e.value;}
\t \t( '+' e=multExpr {\$value += \$e.value;}
\t\t| '-' e=multExpr {\$value -= \$e.value;}
\t\t)*
\t;
\t
multExpr returns [int value]
\t: e = atom {\$value = \$e.value;} ('*' e=atom{\$value *= \$e.value;})*
\t;
\t
atom returns [int value]
\t:\tINT {\$value = Integer.parseInt(\$INT.text);}
\t| ID
\t\t{
\t\t\tInteger v = memory.get(\$ID.text);
\t\t\tif(v!=null) \$value = v;
\t\t\telse System.err.println("undefined variable "+\$ID.text);
\t\t}
\t| '(' expr ')' {\$value = \$expr.value;}
\t;
\t
ID : ('a'..'z' | 'A'..'Z')+ ;
INT : '0'..'9'+ ;
NEWLINE : '\\r'? '\\n';
WS : (' ' | '\\t')+ {skip();};
        """
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "antlr3"

repositories {
    mavenCentral()
}

dependencies{
    compile("org.antlr:antlr-runtime:3.4@jar")
}

        """
    }

}
