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

import java.util.Collections;
import java.util.List;

public abstract class AbstractExpression implements Expression {
    @Override
    public String toString() {
        return getAsSourceText();
    }

    @Override
    public String getAsSourceText() {
        switch (getType()) {
            case QUOTED:
                return '"' + getValue() + '"';
            case SYSTEM:
                return '<' + getValue() + '>';
            case MACRO:
                return getValue();
            case MACRO_FUNCTION:
                return getValue() + "(" + Joiner.on(", ").join(getArguments()) + ")";
            default:
                return getValue() != null ? getValue() : "??";
        }
    }

    @Override
    public List<Expression> getArguments() {
        return Collections.emptyList();
    }
}
