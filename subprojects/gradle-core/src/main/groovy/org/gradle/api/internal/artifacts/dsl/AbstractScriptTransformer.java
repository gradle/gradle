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

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
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

    protected void visitScriptCode(SourceUnit source, GroovyCodeVisitor transformer) {
        source.getAST().getStatementBlock().visit(transformer);
        for (Object method : source.getAST().getMethods()) {
            MethodNode methodNode = (MethodNode) method;
            methodNode.getCode().visit(transformer);
        }
    }

    protected ClassNode getScriptClass(SourceUnit source) {
        if (source.getAST().getStatementBlock().getStatements().isEmpty() && source.getAST().getMethods().isEmpty()) {
            // There is no script class when there are no statements or methods declared in the script
            return null;
        }
        return source.getAST().getClasses().get(0);
    }

    protected void removeMethod(ClassNode declaringClass, MethodNode methodNode) {
        declaringClass.getMethods().remove(methodNode);
        declaringClass.getDeclaredMethods(methodNode.getName()).clear();
    }
}
