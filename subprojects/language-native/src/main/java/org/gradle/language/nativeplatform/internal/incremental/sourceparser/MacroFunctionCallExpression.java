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

import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.IncludeType;

import java.util.List;

/**
 * A macro function call expression.
 */
class MacroFunctionCallExpression extends AbstractExpression {
    private final String value;
    private final List<Expression> arguments;

    MacroFunctionCallExpression(String value, List<Expression> arguments) {
        this.value = value;
        this.arguments = arguments;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public IncludeType getType() {
        return IncludeType.MACRO_FUNCTION;
    }

    @Override
    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        MacroFunctionCallExpression other = (MacroFunctionCallExpression) obj;
        return value.equals(other.value) && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return value.hashCode() ^ arguments.hashCode();
    }
}
