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

/**
 * An expression that has only a type and value and is not a macro function call.
 */
public class SimpleExpression extends AbstractExpression implements Expression {
    private final String value;
    private final IncludeType type;

    SimpleExpression(String value, IncludeType type) {
        this.value = value;
        this.type = type;
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
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SimpleExpression other = (SimpleExpression) obj;
        return value.equals(other.value) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return value.hashCode() ^ type.hashCode();
    }
}
