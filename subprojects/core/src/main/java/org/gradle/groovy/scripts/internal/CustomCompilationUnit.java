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

import com.google.common.collect.ImmutableList;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
class CustomCompilationUnit extends CompilationUnit {

    private static final ImmutableList<Field> OPERATION_FIELDS = operationFields();

    private static ImmutableList<Field> operationFields() {
        ImmutableList.Builder<Field> fields = ImmutableList.builder();
        for (Field field : CompilationUnit.class.getDeclaredFields()) {
            if (field.getType() == IPrimaryClassNodeOperation.class && !Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields.build();
    }

    private final Action<? super ClassNode> customVerifier;
    private boolean classGenerationDecorated;

    public CustomCompilationUnit(CompilerConfiguration compilerConfiguration, CodeSource codeSource, final Action<? super ClassNode> customVerifier, GroovyClassLoader groovyClassLoader, Map<String, List<String>> simpleNameToFQN) {
        super(compilerConfiguration, codeSource, groovyClassLoader);
        this.customVerifier = customVerifier;
        this.resolveVisitor = new GradleResolveVisitor(this, simpleNameToFQN);
        if (!classGenerationDecorated) {
            throw new GradleException("Unable to detect class generation in Groovy CompilationUnit");
        }
    }

    @Override
    public void addPhaseOperation(IPrimaryClassNodeOperation op, int phase) {
        if (phase != Phases.CLASS_GENERATION) {
            super.addPhaseOperation(op, phase);
            return;
        }
        Field field = fieldWithValueOf(op);
        if (field == null) {
            // Operations from elsewhere, such as an AST transformation declared at this phase, have
            // always been discarded here. Not sure if it's correct or not.
            return;
        }
        IPrimaryClassNodeOperation operation = op;
        if (!classGenerationDecorated) {
            operation = decoratedNodeOperation(op);
            setOperation(field, operation);
            classGenerationDecorated = true;
        }
        super.addPhaseOperation(operation, Phases.CLASS_GENERATION);
    }

    private @Nullable Field fieldWithValueOf(IPrimaryClassNodeOperation op) {
        for (Field field : OPERATION_FIELDS) {
            try {
                if (field.get(this) == op) {
                    return field;
                }
            } catch (ReflectiveOperationException e) {
                throw new GradleException("Unable to install custom rules code generation, could not find field holding " + op, e);
            }
        }
        return null;
    }

    private void setOperation(Field field, IPrimaryClassNodeOperation operation) {
        try {
            field.set(this, operation);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Unable to install custom rules code generation, failed to set on field " + field.getName(), e);
        }
    }

    // this is using a decoration of the existing classgen implementation
    // it can't be implemented as a phase as our customVerifier needs to visit closures as well
    private IPrimaryClassNodeOperation decoratedNodeOperation(IPrimaryClassNodeOperation realClassgen) {
        return new IPrimaryClassNodeOperation() {

            @Override
            public boolean needSortedInput() {
                return realClassgen.needSortedInput();
            }

            @Override
            public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
                customVerifier.execute(classNode);
                realClassgen.call(source, context, classNode);
            }
        };
    }

}
