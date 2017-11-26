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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.base.Joiner;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.IncludeType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractExpression implements Expression {
    @Override
    public String toString() {
        return getAsSourceText();
    }

    @Override
    public String getAsSourceText() {
        return format(this);
    }

    @Override
    public List<Expression> getArguments() {
        return Collections.emptyList();
    }

    @Override
    public Expression asMacroExpansion() {
        return asMacroExpansion(this);
    }

    static Expression asMacroExpansion(Expression expression) {
        if (expression.getType() == IncludeType.IDENTIFIER) {
            return new SimpleExpression(expression.getValue(), IncludeType.MACRO);
        }
        if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
            return new ComplexExpression(IncludeType.EXPAND_TOKEN_CONCATENATION, expression.getValue(), expression.getArguments());
        }
        if (expression.getType() == IncludeType.ARGS_LIST && !expression.getArguments().isEmpty()) {
            List<Expression> mapped = new ArrayList<Expression>(expression.getArguments().size());
            for (Expression arg : expression.getArguments()) {
                mapped.add(arg.asMacroExpansion());
            }
            return new ComplexExpression(IncludeType.ARGS_LIST, null, mapped);
        }
        return expression;
    }

    static String format(Expression expression) {
        return format(expression.getType(), expression.getValue(), expression.getArguments());
    }

    static String format(IncludeType type, @Nullable String value, List<Expression> arguments) {
        switch (type) {
            case QUOTED:
                return '"' + value + '"';
            case SYSTEM:
                return '<' + value + '>';
            case MACRO:
            case IDENTIFIER:
            case TOKEN:
                return value;
            case TOKEN_CONCATENATION:
            case EXPAND_TOKEN_CONCATENATION:
                return arguments.get(0).getAsSourceText() + "##" + arguments.get(1).getAsSourceText();
            case MACRO_FUNCTION:
                return value + "(" + Joiner.on(", ").join(arguments) + ")";
            case EXPRESSIONS:
                return Joiner.on(" ").join(arguments);
            case ARGS_LIST:
                return "(" + Joiner.on(", ").join(arguments) + ")";
            default:
                return value != null ? value : "??";
        }
    }
}
