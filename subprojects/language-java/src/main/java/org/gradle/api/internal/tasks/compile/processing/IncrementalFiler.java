/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingResult;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * A decorator for the {@link Filer} which ensures that incremental
 * annotation processors don't break the incremental processing contract.
 */
abstract class IncrementalFiler implements Filer {
    private final Filer delegate;
    private final AnnotationProcessingResult result;
    private final Messager messager;

    IncrementalFiler(Filer delegate, AnnotationProcessingResult result, Messager messager) {
        this.delegate = delegate;
        this.result = result;
        this.messager = messager;
    }

    @Override
    public final JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        recordGeneratedType(name, originatingElements, messager);
        return delegate.createSourceFile(name, originatingElements);
    }

    @Override
    public final JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        recordGeneratedType(name, originatingElements, messager);
        return delegate.createClassFile(name, originatingElements);
    }

    private void recordGeneratedType(CharSequence name, Element[] originatingElements, Messager messager) {
        String generatedType = name.toString();
        Set<String> originatingTypes = getTopLevelTypeNames(originatingElements);
        checkGeneratedType(generatedType, originatingTypes, messager);
        result.addGeneratedType(generatedType, originatingTypes);
    }

    protected abstract void checkGeneratedType(String generatedType, Set<String> originatingTypes, Messager messager);

    private Set<String> getTopLevelTypeNames(Element[] originatingElements) {
        if (originatingElements == null || originatingElements.length == 0) {
            return Collections.emptySet();
        }
        if (originatingElements.length == 1) {
            String topLevelTypeName = getTopLevelTypeName(originatingElements[0]);
            return topLevelTypeName != null ? Collections.singleton(topLevelTypeName) : Collections.<String>emptySet();
        }
        Set<String> typeNames = Sets.newLinkedHashSet();
        for (Element element : originatingElements) {
            String topLevelTypeName = getTopLevelTypeName(element);
            if (topLevelTypeName != null) {
                typeNames.add(topLevelTypeName);
            }
        }
        return typeNames;
    }

    private String getTopLevelTypeName(Element originatingElement) {
        Element current = originatingElement;
        Element parent = originatingElement;
        while (parent != null && !(parent instanceof PackageElement)) {
            current = parent;
            parent = current.getEnclosingElement();
        }
        if (!(current instanceof TypeElement)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors must use types (or elements contained in types) as originating elements.");
            return null;
        }
        return ((TypeElement) current).getQualifiedName().toString();
    }

    @Override
    public final FileObject createResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element... originatingElements) throws IOException {
        messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors are not allowed to create resources.");
        return delegate.createResource(location, pkg, relativeName, originatingElements);
    }

    @Override
    public final FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
        messager.printMessage(Diagnostic.Kind.ERROR, "Incremental annotation processors are not allowed to read resources.");
        return delegate.getResource(location, pkg, relativeName);
    }
}
