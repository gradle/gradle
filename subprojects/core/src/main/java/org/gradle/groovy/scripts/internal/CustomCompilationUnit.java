/*
 * Copyright 2021 the original author or authors.
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

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilationUnit.IPrimaryClassNodeOperation;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
class CustomCompilationUnit extends CompilationUnit {

    public CustomCompilationUnit(CompilerConfiguration compilerConfiguration, CodeSource codeSource, final Action<? super ClassNode> customVerifier, GroovyClassLoader groovyClassLoader, Map<String, List<String>> simpleNameToFQN) {
        super(compilerConfiguration, codeSource, groovyClassLoader);
        this.resolveVisitor = new GradleResolveVisitor(this, simpleNameToFQN);
        installCustomCodegen(customVerifier);
    }

    /**
     * Re-registers the operations that {@link CompilationUnit} itself installs for
     * {@link Phases#CLASS_GENERATION}, because {@link #addPhaseOperation(IPrimaryClassNodeOperation, int)}
     * below discards all of them, and decorates the first one with our {@code customVerifier}.
     *
     * <p>Groovy 4 and earlier used a single {@code classgen} operation, which ran
     * {@link org.codehaus.groovy.classgen.Verifier} itself. Groovy 5 splits that in two: a
     * {@code verification} operation (the {@code Verifier}) followed by {@code classgen}. The
     * {@code Verifier} is what adds the implicit default constructor and the metaclass accessors to
     * a class, so if it is not re-registered here, every class declared in a script is generated
     * without any constructor at all and instantiating it fails at runtime with
     * "Could not find matching constructor for: ...".
     */
    private void installCustomCodegen(Action<? super ClassNode> customVerifier) {
        try {
            // present on Groovy 5+ only
            final Field verification = findOperationField("verification");
            final Field classgen = getOperationField("classgen");

            // the customVerifier has to run before the Verifier does, as it did on Groovy 4
            final Field firstField = verification != null ? verification : classgen;
            final IPrimaryClassNodeOperation first = decoratedNodeOperation(customVerifier, getOperation(firstField));
            firstField.set(this, first);

            // addFirstPhaseOperation pushes to the front, so register in reverse order
            if (verification != null) {
                addFirstPhaseOperation(getOperation(classgen), Phases.CLASS_GENERATION);
            }
            addFirstPhaseOperation(first, Phases.CLASS_GENERATION);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Unable to install custom rules code generation", e);
        }
    }

    @Override
    public void addPhaseOperation(IPrimaryClassNodeOperation op, int phase) {
        if (phase != Phases.CLASS_GENERATION) {
            super.addPhaseOperation(op, phase);
        }
    }

    private IPrimaryClassNodeOperation getOperation(Field field) throws ReflectiveOperationException {
        return (IPrimaryClassNodeOperation) field.get(this);
    }

    private static Field getOperationField(String name) {
        final Field field = findOperationField(name);
        if (field == null) {
            throw new GradleException("Unable to detect class generation in Groovy CompilationUnit");
        }
        return field;
    }

    @Nullable
    private static Field findOperationField(String name) {
        try {
            final Field field = CompilationUnit.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    // this is using a decoration of the existing operation
    // it can't be implemented as a phase as our customVerifier needs to visit closures as well
    private static IPrimaryClassNodeOperation decoratedNodeOperation(Action<? super ClassNode> customVerifier, IPrimaryClassNodeOperation decorated) {
        return new IPrimaryClassNodeOperation() {

            @Override
            public boolean needSortedInput() {
                return decorated.needSortedInput();
            }

            @Override
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                customVerifier.execute(classNode);
                decorated.call(source, context, classNode);
            }
        };
    }

}
