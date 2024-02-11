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
import org.gradle.language.nativeplatform.internal.IncludeType;

import javax.annotation.Nullable;

/**
 * An "object-like" macro #define whose body is an expression with no arguments.
 */
public class MacroWithSimpleExpression extends AbstractMacro {
    private final IncludeType includeType;
    private final String value;

    public MacroWithSimpleExpression(String name, IncludeType includeType, @Nullable String value) {
        super(name);
        this.includeType = includeType;
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public IncludeType getType() {
        return includeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        MacroWithSimpleExpression other = (MacroWithSimpleExpression) obj;
        return Objects.equal(value, other.value);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (value == null ? 0 : value.hashCode());
    }
}
