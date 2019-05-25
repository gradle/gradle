/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.collect.ImmutableList
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.language.nativeplatform.internal.IncludeType

class IncludeDirectivesSerializerTest extends SerializerSpec {
    def "serializes empty directives"() {
        def directives = DefaultIncludeDirectives.of(ImmutableList.of(), ImmutableList.of(), ImmutableList.of())

        expect:
        serialize(directives, IncludeDirectivesSerializer.INSTANCE) == directives
    }

    def "serializes include directives"() {
        def include1 = new IncludeWithSimpleExpression("one.h", true, IncludeType.QUOTED)
        def include2 = new IncludeWithSimpleExpression("two.h", true, IncludeType.SYSTEM)
        def include3 = new IncludeWithSimpleExpression("three.h", false, IncludeType.MACRO)
        def include4 = new IncludeWithMacroFunctionCallExpression("A", true, ImmutableList.of(new SimpleExpression("X", IncludeType.MACRO)))
        def include5 = new IncludeWithMacroFunctionCallExpression("A", true, ImmutableList.of(new SimpleExpression("X", IncludeType.MACRO), new SimpleExpression("Y", IncludeType.MACRO)))
        def directives = DefaultIncludeDirectives.of(ImmutableList.copyOf([include1, include2, include3, include4, include5]), ImmutableList.of(), ImmutableList.of())

        expect:
        serialize(directives, IncludeDirectivesSerializer.INSTANCE) == directives
    }

    def "serializes nested expression"() {
        def expression1 = new ComplexExpression(IncludeType.MACRO_FUNCTION, "X", [new SimpleExpression("Y", IncludeType.MACRO)])
        def expression2 = new ComplexExpression(IncludeType.MACRO_FUNCTION, "X", [new SimpleExpression("Y", IncludeType.MACRO), new SimpleExpression("Z", IncludeType.MACRO)])
        def expression3 = new ComplexExpression(IncludeType.TOKEN_CONCATENATION, null, [new SimpleExpression("X", IncludeType.IDENTIFIER), new SimpleExpression("Y", IncludeType.IDENTIFIER)])
        def include = new IncludeWithMacroFunctionCallExpression("A", true, ImmutableList.of(expression1, expression2, expression3))
        def directives = DefaultIncludeDirectives.of(ImmutableList.copyOf([include]), ImmutableList.of(), ImmutableList.of())

        expect:
        serialize(directives, IncludeDirectivesSerializer.INSTANCE) == directives
    }

    def "replaces common expressions with constants"() {
        def include = new IncludeWithMacroFunctionCallExpression("A", true, ImmutableList.of(
            new SimpleExpression(null, IncludeType.EXPRESSIONS),
            new SimpleExpression(null, IncludeType.ARGS_LIST),
            new SimpleExpression(",", IncludeType.TOKEN),
            new SimpleExpression("(", IncludeType.TOKEN),
            new SimpleExpression(")", IncludeType.TOKEN)))
        def directives = DefaultIncludeDirectives.of(ImmutableList.copyOf([include]), ImmutableList.of(), ImmutableList.of())

        expect:
        def expressions = serialize(directives, IncludeDirectivesSerializer.INSTANCE).all.first().arguments
        expressions[0].is(SimpleExpression.EMPTY_EXPRESSIONS)
        expressions[1].is(SimpleExpression.EMPTY_ARGS)
        expressions[2].is(SimpleExpression.COMMA)
        expressions[3].is(SimpleExpression.LEFT_PAREN)
        expressions[4].is(SimpleExpression.RIGHT_PAREN)
    }

    def "serializes macro directives"() {
        def macro1 = new MacroWithSimpleExpression("ONE", IncludeType.QUOTED, "one")
        def macro2 = new MacroWithSimpleExpression("TWO", IncludeType.MACRO, "two")
        def macro3 = new MacroWithComplexExpression("THREE", IncludeType.MACRO_FUNCTION, "abc", [])
        def macro4 = new MacroWithComplexExpression("FOUR", IncludeType.MACRO_FUNCTION, "abc", [new SimpleExpression("abc.h", IncludeType.QUOTED)])
        def macro5 = new MacroWithComplexExpression("FIVE", IncludeType.MACRO_FUNCTION, "abc", [new ComplexExpression(IncludeType.MACRO_FUNCTION, "macro", [new SimpleExpression("abc.h", IncludeType.QUOTED)])])
        def macro6 = new MacroWithComplexExpression("SIX", IncludeType.TOKEN_CONCATENATION, null, [new SimpleExpression("X", IncludeType.IDENTIFIER), new SimpleExpression("Y", IncludeType.IDENTIFIER)])
        def macro7 = new UnresolvableMacro("SEVEN")
        def macro8 = new UnresolvableMacro("EIGHT")
        def directives = DefaultIncludeDirectives.of(ImmutableList.of(), ImmutableList.copyOf([macro1, macro2, macro3, macro4, macro5, macro6, macro7, macro8]), ImmutableList.of())

        expect:
        serialize(directives, IncludeDirectivesSerializer.INSTANCE) == directives
    }

    def "serializes macro function directives"() {
        def macro1 = new ReturnFixedValueMacroFunction("ONE", 0, IncludeType.QUOTED, "one", [])
        def macro2 = new ReturnFixedValueMacroFunction("TWO", 3, IncludeType.MACRO, "two", [new SimpleExpression("abc", IncludeType.MACRO)])
        def macro3 = new ReturnParameterMacroFunction("THREE", 12, 4)
        def macro4 = new ArgsMappingMacroFunction("FOUR", 3, [0, 1, 2] as int[], IncludeType.MACRO_FUNCTION, "macro", [new SimpleExpression("abc.h", IncludeType.QUOTED)])
        def macro5 = new ArgsMappingMacroFunction("FOUR", 3, [2, 1] as int[], IncludeType.TOKEN_CONCATENATION, null, [new SimpleExpression("abc.h", IncludeType.QUOTED)])
        def macro6 = new UnresolvableMacroFunction("SIX", 3)
        def macro7 = new UnresolvableMacroFunction("SEVEN", 3)
        def directives = DefaultIncludeDirectives.of(ImmutableList.of(), ImmutableList.of(), ImmutableList.copyOf([macro1, macro2, macro3, macro4, macro5, macro6, macro7]))

        expect:
        serialize(directives, IncludeDirectivesSerializer.INSTANCE) == directives
    }
}
