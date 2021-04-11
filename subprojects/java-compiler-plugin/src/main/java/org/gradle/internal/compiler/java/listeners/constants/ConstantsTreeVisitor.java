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
import com.sun.source.tree.PackageTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

public class ConstantsTreeVisitor extends TreePathScanner<Collection<String>, Collection<String>> {

    private final Elements elements;
    private final Trees trees;
    private final Map<String, Collection<String>> mapping;

    public ConstantsTreeVisitor(Elements elements, Trees trees, Map<String, Collection<String>> mapping) {
        this.elements = elements;
        this.trees = trees;
        this.mapping = mapping;
    }

    @Override
    public Collection<String> visitCompilationUnit(CompilationUnitTree node, Collection<String> strings) {
        return super.visitCompilationUnit(node, strings);
    }

    @Override
    @SuppressWarnings("Since15")
    public Collection<String> visitPackage(PackageTree node, Collection<String> collectedClasses) {
        Element element = trees.getElement(getCurrentPath());

        // Collect classes for visited class
        String visitedClass = ((Symbol.TypeSymbol) element).getQualifiedName().toString();
        super.visitPackage(node, mapping.computeIfAbsent(visitedClass, k -> new HashSet<>()));
        // Remove self
        mapping.get(visitedClass).remove(visitedClass);

        // Return back previous collected classes
        return collectedClasses;
    }

    @Override
    public Collection<String> visitClass(ClassTree node, Collection<String> collectedClasses) {
        Element element = trees.getElement(getCurrentPath());

        // Collect classes for visited class
        String visitedClass = getBinaryClassName((TypeElement) element);
        super.visitClass(node, mapping.computeIfAbsent(visitedClass, k -> new HashSet<>()));
        // Remove self
        mapping.get(visitedClass).remove(visitedClass);

        // Return back previous collected classes
        return collectedClasses;
    }

    @Override
    public Collection<String> visitMemberSelect(MemberSelectTree node, Collection<String> collectedClasses) {
        Element element = trees.getElement(getCurrentPath());
        if (isPrimitiveConstantVariable(element)) {
            collectedClasses.add(getBinaryClassName((TypeElement) element.getEnclosingElement()));
        }
        return super.visitMemberSelect(node, collectedClasses);
    }

    @Override
    public Collection<String> visitIdentifier(IdentifierTree node, Collection<String> collectedClasses) {
        Element element = trees.getElement(getCurrentPath());

        if (isPrimitiveConstantVariable(element)) {
            collectedClasses.add(getBinaryClassName((TypeElement) element.getEnclosingElement()));
        }
        return super.visitIdentifier(node, collectedClasses);
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
