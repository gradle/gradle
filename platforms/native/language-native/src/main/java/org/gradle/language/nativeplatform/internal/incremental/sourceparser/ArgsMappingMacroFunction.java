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

import com.google.common.base.Objects;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.IncludeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ArgsMappingMacroFunction extends AbstractMacroFunction {
    // Keep the argument from this expression
    static final int KEEP = -1;
    // Map the arguments of the argument from this expression
    static final int REPLACE_ARGS = -2;
    private final int[] argsMap;
    private final IncludeType type;
    private final String value;
    private final List<Expression> arguments;

    public ArgsMappingMacroFunction(String macroName, int parameters, int[] argsMap, IncludeType type,  String value, List<Expression> arguments) {
        super(macroName, parameters);
        this.argsMap = argsMap;
        this.type = type;
        this.value = value;
        this.arguments = arguments;
    }

    @Override
    protected String getBody() {
        return AbstractExpression.format(type, value, arguments);
    }

    public IncludeType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    public int[] getArgsMap() {
        return argsMap;
    }

    @Override
    public Expression evaluate(List<Expression> arguments) {
        List<Expression> mapped = new ArrayList<Expression>(this.arguments.size());
        int currentMapPos = 0;
        for (Expression argument : this.arguments) {
            currentMapPos = mapInto(argument, arguments, currentMapPos, mapped);
        }
        return new ComplexExpression(type, value, mapped);
    }

    private int mapInto(Expression expression, List<Expression> arguments, int currentMapPos, List<Expression> mapped) {
        int replaceWith = argsMap[currentMapPos];
        currentMapPos++;
        if (replaceWith == KEEP) {
            // Keep this expression
            mapped.add(expression);
        } else if (replaceWith == REPLACE_ARGS) {
            // Map the arguments of this expression
            List<Expression> mappedArgs = new ArrayList<Expression>(expression.getArguments().size());
            for (Expression arg : expression.getArguments()) {
                currentMapPos = mapInto(arg, arguments, currentMapPos, mappedArgs);
            }
            mapped.add(new ComplexExpression(expression.getType(), expression.getValue(), mappedArgs));
        } else if (type == IncludeType.MACRO_FUNCTION) {
            // Macro expand parameter
            mapped.add(arguments.get(replaceWith).asMacroExpansion());
        } else {
            // Do not macro expand parameter
            mapped.add(arguments.get(replaceWith));
        }
        return currentMapPos;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ArgsMappingMacroFunction other = (ArgsMappingMacroFunction) obj;
        return type == other.type && Objects.equal(value, other.value) && Arrays.equals(argsMap, other.argsMap) && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ type.hashCode() ^ arguments.hashCode() ^ Arrays.hashCode(argsMap);
    }
}
