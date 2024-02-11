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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeType;

/**
 * An #include directive whose body is an expression with no arguments.
 */
public class IncludeWithSimpleExpression extends AbstractInclude {

    private static final Interner<IncludeWithSimpleExpression> INTERNER = Interners.newWeakInterner();

    private final String value;
    private final boolean isImport;
    private final IncludeType type;

    public static IncludeWithSimpleExpression create(String value, boolean isImport, IncludeType type) {
        return INTERNER.intern(new IncludeWithSimpleExpression(value, isImport, type));
    }

    public static Include create(Expression expression, boolean isImport) {
        if (expression.getType() == IncludeType.MACRO_FUNCTION && !expression.getArguments().isEmpty()) {
            return new IncludeWithMacroFunctionCallExpression(expression.getValue(), isImport, ImmutableList.copyOf(expression.getArguments()));
        }
        return create(expression.getValue(), isImport, expression.getType());
    }

    public IncludeWithSimpleExpression(String value, boolean isImport, IncludeType type) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        this.value = value;
        this.isImport = isImport;
        this.type = type;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public boolean isImport() {
        return isImport;
    }

    @Override
    public IncludeType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IncludeWithSimpleExpression that = (IncludeWithSimpleExpression) o;

        if (isImport != that.isImport) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (!value.equals(that.value)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = value.hashCode();
        result = 31 * result + (isImport ? 1 : 0);
        result = 31 * result + type.hashCode();
        return result;
    }
}
