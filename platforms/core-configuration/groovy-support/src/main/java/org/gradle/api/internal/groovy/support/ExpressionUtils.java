/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.groovy.support;

import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ExpressionTransformer;

final class ExpressionUtils {
    private ExpressionUtils() {}

    private static final ExpressionTransformer COPY_TRANSFORMER = new ExpressionTransformer() {
        @Override
        public Expression transform(Expression expression) {
            return expression.transformExpression(this);
        }
    };

    /**
     * Creates a deep-ish copy of the expression. This method relies on traverse capabilities of {@link Expression#transformExpression(ExpressionTransformer)}.
     *
     * @param expression the expression to copy
     * @param <T> the type of the expression
     * @return the deep copy of the expression
     */
    @SuppressWarnings("unchecked")
    public static <T extends Expression> T copyExpression(T expression) {
        return (T) expression.transformExpression(COPY_TRANSFORMER);
    }
}
