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

    static String format(Expression expression) {
        IncludeType type = expression.getType();
        String value = expression.getValue();
        List<Expression> arguments = expression.getArguments();
        switch (type) {
            case QUOTED:
                return '"' + value + '"';
            case SYSTEM:
                return '<' + value + '>';
            case MACRO:
                return value;
            case MACRO_FUNCTION:
                return value + "(" + Joiner.on(", ").join(arguments) + ")";
            default:
                return value != null ? value : "??";
        }
    }
}
