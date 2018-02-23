/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.transform;

import net.jcip.annotations.ThreadSafe;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;

/**
 * The verifier is the only thing in the Groovy compiler chain that gets to visit the classes generated from closure expressions. If we want to transform these classes, we have to do it with this
 * *hack*.
 */
@ThreadSafe
public class ClosureCreationInterceptingVerifier implements Action<ClassNode> {

    public static final Action<ClassNode> INSTANCE = new ClosureCreationInterceptingVerifier();

    @Override
    public void execute(ClassNode node) {
        if (node.implementsInterface(ClassHelper.GENERATED_CLOSURE_Type)) {
            RulesVisitor.visitGeneratedClosure(node);
            RuleVisitor.visitGeneratedClosure(node);
        }
    }
}
