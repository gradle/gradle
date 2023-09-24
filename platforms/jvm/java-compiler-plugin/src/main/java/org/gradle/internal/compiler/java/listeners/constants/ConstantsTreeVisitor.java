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

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.ModuleTree;
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

    private static final String PACKAGE_INFO_JAVA = "package-info.java";
    private static final String PACKAGE_INFO = "package-info";
    private static final String MODULE_INFO = "module-info";

    private final Elements elements;
    private final Trees trees;
    private final ConstantDependentsConsumer consumer;

    public ConstantsTreeVisitor(Elements elements, Trees trees, ConstantDependentsConsumer consumer) {
        this.elements = elements;
        this.trees = trees;
        this.consumer = consumer;
    }

    @Override
    public ConstantsVisitorContext visitCompilationUnit(CompilationUnitTree node, ConstantsVisitorContext context) {
        // For JDK8 visitPackage is not called for package-info.java, so we have to resolve package-info from compilation unit
        String sourceName = node.getSourceFile().getName();
        if (sourceName.endsWith(PACKAGE_INFO_JAVA)) {
            PackageElement packageElement = elements.getPackageOf(trees.getElement(getCurrentPath()));
            String visitedPackageInfo = packageElement == null || packageElement.getQualifiedName().toString().isEmpty()
                ? PACKAGE_INFO
                : packageElement.getQualifiedName().toString() + "." + PACKAGE_INFO;
            return super.visitCompilationUnit(node, new ConstantsVisitorContext(visitedPackageInfo, consumer::consumeAccessibleDependent));
        }
        return super.visitCompilationUnit(node, context);
    }

    @Override
    @SuppressWarnings("Since15")
    public ConstantsVisitorContext visitModule(ModuleTree node, ConstantsVisitorContext context) {
        super.visitModule(node, new ConstantsVisitorContext(MODULE_INFO, consumer::consumeAccessibleDependent));
        return context;
    }

    @Override
    public ConstantsVisitorContext visitClass(ClassTree node, ConstantsVisitorContext context) {
        Element element = trees.getElement(getCurrentPath());

        String visitedClass = getBinaryClassName((TypeElement) element);
        super.visitClass(node, new ConstantsVisitorContext(visitedClass, consumer::consumePrivateDependent));

        return context;
    }

    @Override
    public ConstantsVisitorContext visitVariable(VariableTree node, ConstantsVisitorContext context) {
        if (isAccessibleConstantVariableDeclaration(node) && node.getInitializer() != null && node.getInitializer().getKind() != METHOD_INVOCATION) {
            // We now just check, that constant declaration is not `static {}` or `CONSTANT = methodInvocation()`,
            // but it could be further optimized to check if expression is one that can be inlined or not.
            return super.visitVariable(node, new ConstantsVisitorContext(context.getVisitedClass(), consumer::consumeAccessibleDependent));
        } else {
            return super.visitVariable(node, context);
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
