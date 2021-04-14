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

package org.gradle.internal.compiler.java.listeners.constants;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.Set;

import static com.sun.source.tree.Tree.Kind.METHOD_INVOCATION;

public class ConstantsTreeVisitor extends TreePathScanner<ConstantsVisitorContext, ConstantsVisitorContext> {

    private final Elements elements;
    private final Trees trees;
    private final ConstantDependentsConsumer consumer;

    public ConstantsTreeVisitor(Elements elements, Trees trees, ConstantDependentsConsumer consumer) {
        this.elements = elements;
        this.trees = trees;
        this.consumer = consumer;
    }

    @Override
    public ConstantsVisitorContext visitCompilationUnit(CompilationUnitTree node, ConstantsVisitorContext constantConsumer) {
        return super.visitCompilationUnit(node, constantConsumer);
    }

    @Override
    public ConstantsVisitorContext visitAssignment(AssignmentTree node, ConstantsVisitorContext constantConsumer) {
        return super.visitAssignment(node, constantConsumer);
    }

    @Override
    @SuppressWarnings("Since15")
    public ConstantsVisitorContext visitPackage(PackageTree node, ConstantsVisitorContext constantConsumer) {
        Element element = trees.getElement(getCurrentPath());

        // Collect classes for visited class
        String visitedClass = ((PackageElement) element).getQualifiedName().toString();
        // Always add self, so we know this class was visited
        consumer.consumePrivateDependent(visitedClass, visitedClass);
        super.visitPackage(node, new ConstantsVisitorContext(visitedClass, consumer::consumePrivateDependent));

        // Return back previous collected classes
        return constantConsumer;
    }

    @Override
    public ConstantsVisitorContext visitClass(ClassTree node, ConstantsVisitorContext constantConsumer) {
        Element element = trees.getElement(getCurrentPath());

        // Collect classes for visited class
        String visitedClass = getBinaryClassName((TypeElement) element);
        // Always add self, so we know this class was visited
        consumer.consumePrivateDependent(visitedClass, visitedClass);
        super.visitClass(node, new ConstantsVisitorContext(visitedClass, consumer::consumePrivateDependent));

        // Return back previous collected classes
        return constantConsumer;
    }

    @Override
    public ConstantsVisitorContext visitVariable(VariableTree node, ConstantsVisitorContext constantConsumer) {
        if (isAccessibleConstantVariableDeclaration(node) && node.getInitializer() != null && node.getInitializer().getKind() != METHOD_INVOCATION) {
            // We now just check, that constant declaration is not `static {}` or `CONSTANT = methodInvocation()`,
            // but it could be further optimized to check if expression is one that can be inlined or not.
            return super.visitVariable(node, new ConstantsVisitorContext(constantConsumer.getVisitedClass(), consumer::consumeAccessibleDependent));
        } else {
            return super.visitVariable(node, constantConsumer);
        }
    }

    private boolean isAccessibleConstantVariableDeclaration(VariableTree node) {
        Set<Modifier> modifiers = node.getModifiers().getFlags();
        return modifiers.contains(Modifier.FINAL) && modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PRIVATE);
    }

    @Override
    public ConstantsVisitorContext visitMemberSelect(MemberSelectTree node, ConstantsVisitorContext context) {
        Element element = trees.getElement(getCurrentPath());
        if (isPrimitiveConstantVariable(element)) {
            context.addConstantOrigin(getBinaryClassName((TypeElement) element.getEnclosingElement()));
        }
        return super.visitMemberSelect(node, context);
    }

    @Override
    public ConstantsVisitorContext visitIdentifier(IdentifierTree node, ConstantsVisitorContext context) {
        Element element = trees.getElement(getCurrentPath());

        if (isPrimitiveConstantVariable(element)) {
            context.addConstantOrigin(getBinaryClassName((TypeElement) element.getEnclosingElement()));
        }
        return super.visitIdentifier(node, context);
    }

    private String getBinaryClassName(TypeElement typeElement) {
        if (typeElement.getNestingKind().isNested()) {
            return elements.getBinaryName(typeElement).toString();
        } else {
            return typeElement.getQualifiedName().toString();
        }
    }

    private boolean isPrimitiveConstantVariable(Element element) {
        return element instanceof VariableElement
            && element.getEnclosingElement() instanceof TypeElement
            && ((VariableElement) element).getConstantValue() != null;
    }

}
