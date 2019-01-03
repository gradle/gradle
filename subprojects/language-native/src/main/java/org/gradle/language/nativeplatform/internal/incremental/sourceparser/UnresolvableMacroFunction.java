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
 * A macro function whose body cannot be resolved.
 */
public class UnresolvableMacroFunction extends AbstractMacroFunction {
    private static final SimpleExpression UNRESOLVED_EXPRESSION = new SimpleExpression(null, IncludeType.OTHER);

    public UnresolvableMacroFunction(String name, int parameters) {
        super(name, parameters);
    }

    @Override
    public Expression evaluate(List<Expression> arguments) {
        return UNRESOLVED_EXPRESSION;
    }
}
