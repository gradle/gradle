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
 * Excludes everything from a script except statements that are satisfied by a given predicate, and imports for accessible classes. <p> *All* other kinds of constructs are filtered, including:
 * classes, methods etc.
 */
public class StatementExtractingScriptTransformer extends AbstractScriptTransformer {

    private final String id;
    private final StatementTransformer transformer;

    public StatementExtractingScriptTransformer(String id, StatementTransformer transformer) {
        this.id = id;
        this.transformer = transformer;
    }

    public String getId() {
        return id;
    }

    protected int getPhase() {
        return Phases.CONVERSION;
    }

    public void call(SourceUnit source) throws CompilationFailedException {
        AstUtils.filterAndTransformStatements(source, transformer);

        // Filter imported classes which are not available yet

        Iterator<ImportNode> iter = source.getAST().getImports().iterator();
        while (iter.hasNext()) {
            ImportNode importedClass = iter.next();
            if (!AstUtils.isVisible(source, importedClass.getClassName())) {
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
            if (!AstUtils.isVisible(source, importedClass.getClassName())) {
                iter.remove();
            }
        }

        iter = source.getAST().getStaticStarImports().values().iterator();
        while (iter.hasNext()) {
            ImportNode importedClass = iter.next();
            if (!AstUtils.isVisible(source, importedClass.getClassName())) {
                iter.remove();
            }
        }

        ClassNode scriptClass = AstUtils.getScriptClass(source);

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
                    AstUtils.removeMethod(scriptClass, methodNode);
                }
            }
        }

        source.getAST().getMethods().clear();
    }

    public Transformer invert() {
        return new Inverse("no_" + StatementExtractingScriptTransformer.this.getId(), Specs.not(transformer.getSpec()));
    }

    private static class Inverse extends AbstractScriptTransformer {
        private final String id;
        private final Spec<? super Statement> spec;

        private Inverse(String id, Spec<? super Statement> spec) {
            this.id = id;
            this.spec = spec;
        }

        protected int getPhase() {
            return Phases.CANONICALIZATION;
        }

        public String getId() {
            return id;
        }

        @Override
        public void call(SourceUnit source) throws CompilationFailedException {
            AstUtils.filterAndTransformStatements(source, new FilteringStatementTransformer(spec));
        }
    }

}
