/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;

public class ScriptBlock {
    private final String name;
    private final MethodCallExpression methodCall;
    private final ClosureExpression closureExpression;

    public ScriptBlock(String name, MethodCallExpression methodCall, ClosureExpression closureExpression) {
        this.name = name;
        this.methodCall = methodCall;
        this.closureExpression = closureExpression;
    }

    public String getName() {
        return name;
    }

    public MethodCallExpression getMethodCall() {
        return methodCall;
    }

    public ClosureExpression getClosureExpression() {
        return closureExpression;
    }
}
