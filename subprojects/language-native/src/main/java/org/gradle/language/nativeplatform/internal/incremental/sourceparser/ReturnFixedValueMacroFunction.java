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

import javax.annotation.Nullable;
import java.util.List;

/**
 * A macro function that returns a fixed expression and ignores its parameters.
 */
public class ReturnFixedValueMacroFunction extends AbstractMacroFunction implements Expression {
    private final String value;
    private final IncludeType type;
    private final List<Expression> arguments;

    public ReturnFixedValueMacroFunction(String name, int parameters, IncludeType type, @Nullable String value, List<Expression> arguments) {
        super(name, parameters);
        this.value = value;
        this.type = type;
        this.arguments = arguments;
    }

    @Override
    protected String getBody() {
        return getAsSourceText();
    }

    @Override
    public Expression asMacroExpansion() {
        return AbstractExpression.asMacroExpansion(this);
    }

    @Override
    public String getAsSourceText() {
        return AbstractExpression.format(this);
    }

    @Override
    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public IncludeType getType() {
        return type;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public Expression evaluate(List<Expression> arguments) {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ReturnFixedValueMacroFunction other = (ReturnFixedValueMacroFunction) obj;
        return Objects.equal(value, other.value) && type == other.type && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (value == null ? 0 : value.hashCode()) ^ type.hashCode() ^ arguments.hashCode();
    }
}
