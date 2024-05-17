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
 * An "object-like" macro #define whose body is an expression with arguments.
 */
public class MacroWithComplexExpression extends AbstractMacro {
    private final IncludeType type;
    private final String value;
    private final List<Expression> arguments;

    public MacroWithComplexExpression(String name, IncludeType type, @Nullable String value, List<Expression> arguments) {
        super(name);
        this.type = type;
        this.value = value;
        this.arguments = arguments;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public IncludeType getType() {
        return type;
    }

    @Override
    public List<Expression> getArguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        MacroWithComplexExpression other = (MacroWithComplexExpression) obj;
        return type == other.type && Objects.equal(value, other.value) && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ type.hashCode() ^ (value == null ? 0 : value.hashCode()) ^ arguments.hashCode();
    }
}
