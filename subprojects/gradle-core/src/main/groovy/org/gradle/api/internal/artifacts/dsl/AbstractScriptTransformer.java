/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl;

import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.ast.expr.*;
import org.gradle.groovy.scripts.Transformer;

public abstract class AbstractScriptTransformer extends CompilationUnit.SourceUnitOperation implements Transformer {
    public void register(CompilationUnit compilationUnit) {
        compilationUnit.addPhaseOperation(this, getPhase());
    }

    protected abstract int getPhase();

    protected boolean isMethodOnThis(MethodCallExpression call, String name) {
        boolean isTaskMethod = call.getMethod() instanceof ConstantExpression && call.getMethod().getText().equals(
                name);
        return isTaskMethod && targetIsThis(call);
    }

    protected boolean targetIsThis(MethodCallExpression call) {
        Expression target = call.getObjectExpression();
        return target instanceof VariableExpression && target.getText().equals("this");
    }
}
