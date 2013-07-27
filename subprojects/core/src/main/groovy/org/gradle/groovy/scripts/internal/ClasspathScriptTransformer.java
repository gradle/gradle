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

package org.gradle.groovy.scripts.internal;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.UncheckedException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * The classpath script transformer uses Groovy's AST support to implement a two-phase
 * compilation of a script into a "class path script" and an "everything else script".
 * The classpath script can then be executed and its results taken into account (in
 * particular, to update the classpath) before the remainder of the script is executed.
 */
public abstract class ClasspathScriptTransformer extends AbstractScriptTransformer {
    protected abstract String getScriptMethodName();

    protected int getPhase() {
        return Phases.CONVERSION;
    }

    public void call(SourceUnit source) throws CompilationFailedException {
        Spec<Statement> spec = isScriptBlock();
        filterStatements(source, spec);

        // Filter imported classes which are not available yet

        Iterator<ImportNode> iter = source.getAST().getImports().iterator();
        while (iter.hasNext()) {
            ImportNode importedClass = iter.next();
            if (!isVisible(source, importedClass.getClassName())) {
                try {
                    Field field = ModuleNode.class.getDeclaredField("imports");
                    field.setAccessible(true);
                    Map value = (Map) field.get(source.getAST());
                    value.remove(importedClass.getAlias());
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        iter = source.getAST().getStaticImports().values().iterator();
        while (iter.hasNext()) {
            ImportNode importedClass = iter.next();
            if (!isVisible(source, importedClass.getClassName())) {
                iter.remove();
            }
        }

        iter = source.getAST().getStaticStarImports().values().iterator();
        while (iter.hasNext()) {
            ImportNode importedClass = iter.next();
            if (!isVisible(source, importedClass.getClassName())) {
                iter.remove();
            }
        }

        ClassNode scriptClass = getScriptClass(source);

        // Remove all the classes other than the main class
        Iterator<ClassNode> classes = source.getAST().getClasses().iterator();
        while (classes.hasNext()) {
            ClassNode classNode = classes.next();
            if (classNode != scriptClass) {
                classes.remove();
            }
        }

        // Remove all the methods from the main class
        if (scriptClass != null) {
            for (MethodNode methodNode : new ArrayList<MethodNode>(scriptClass.getMethods())) {
                if (!methodNode.getName().equals("run")) {
                    removeMethod(scriptClass, methodNode);
                }
            }
        }

        source.getAST().getMethods().clear();
    }

    private boolean isVisible(SourceUnit source, String className) {
        try {
            source.getClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void filterStatements(SourceUnit source, Spec<Statement> spec) {
        Iterator statementIterator = source.getAST().getStatementBlock().getStatements().iterator();
        while (statementIterator.hasNext()) {
            Statement statement = (Statement) statementIterator.next();
            if (!spec.isSatisfiedBy(statement)) {
                statementIterator.remove();
            }
        }
    }

    public Transformer invert() {
        return new AbstractScriptTransformer() {
            protected int getPhase() {
                return Phases.CANONICALIZATION;
            }

            public String getId() {
                return "no_" + ClasspathScriptTransformer.this.getId();
            }

            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                Spec<Statement> spec = Specs.not(isScriptBlock());
                filterStatements(source, spec);
            }
        };
    }

    public Spec<Statement> isScriptBlock() {
        return new Spec<Statement>() {
            public boolean isSatisfiedBy(Statement statement) {
                if (!(statement instanceof ExpressionStatement)) {
                    return false;
                }

                ExpressionStatement expressionStatement = (ExpressionStatement) statement;
                if (!(expressionStatement.getExpression() instanceof MethodCallExpression)) {
                    return false;
                }

                MethodCallExpression methodCall = (MethodCallExpression) expressionStatement.getExpression();
                if (!isMethodOnThis(methodCall, getScriptMethodName())) {
                    return false;
                }

                if (!(methodCall.getArguments() instanceof ArgumentListExpression)) {
                    return false;
                }

                ArgumentListExpression args = (ArgumentListExpression) methodCall.getArguments();
                return args.getExpressions().size() == 1 && args.getExpression(0) instanceof ClosureExpression;
            }
        };
    }
}
