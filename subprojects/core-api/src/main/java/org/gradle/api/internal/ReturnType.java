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
package org.gradle.api.internal;

import groovy.transform.stc.SingleSignatureClosureHint;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

/**
 * <p>A hint used to instruct the type checker to pick the return parameter type as the type of the
 * sole argument to the closure. For example, imagine the method:</p>
 *
 * <p>R foo(A someArg, Closure c) {...}</p>
 *
 * <p>Here, the sole parameter for Closure c would be of type R, as R is the return type of foo.</p>
 */
public final class ReturnType extends SingleSignatureClosureHint {
    @Override
    public ClassNode[] getParameterTypes(MethodNode node, String[] options, SourceUnit sourceUnit, CompilationUnit unit, ASTNode usage) {
        return new ClassNode[]{node.getReturnType()};
    }
}
