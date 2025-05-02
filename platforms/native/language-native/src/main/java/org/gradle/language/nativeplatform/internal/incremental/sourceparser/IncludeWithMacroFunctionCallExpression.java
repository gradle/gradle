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

import com.google.common.collect.ImmutableList;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.IncludeType;

import java.util.List;

/**
 * An #include directive whose body is a macro function call.
 */
public class IncludeWithMacroFunctionCallExpression extends AbstractInclude {
    private final String name;
    private final boolean isImport;
    private final ImmutableList<Expression> arguments;

    public IncludeWithMacroFunctionCallExpression(String name, boolean isImport, ImmutableList<Expression> arguments) {
        this.name = name;
        this.isImport = isImport;
        this.arguments = arguments;
    }

    @Override
    public IncludeType getType() {
        return IncludeType.MACRO_FUNCTION;
    }

    @Override
    public String getValue() {
        return name;
    }

    @Override
    public boolean isImport() {
        return isImport;
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
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        IncludeWithMacroFunctionCallExpression other = (IncludeWithMacroFunctionCallExpression) obj;
        return name.equals(other.name) && isImport == other.isImport && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ arguments.hashCode();
    }
}
