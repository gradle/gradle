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

package org.gradle.language.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType

/**
 * A processor that collects all types annotated with the @Service annotation
 * and generates a single ServiceRegistry class from them. The registry has
 * one getter for each annotated type, which calls the no-argument constructor
 * of that type.
 */
@CompileStatic
class ServiceRegistryProcessorFixture extends AnnotationProcessorFixture {

    ServiceRegistryProcessorFixture() {
        super("Service")
        declaredType = IncrementalAnnotationProcessorType.MULTIPLE_ORIGIN
    }

    String getGeneratorCode() {
        """
String className = "ServiceRegistry";
try {
    JavaFileObject sourceFile = filer.createSourceFile(className, elements.toArray(new Element[0]));
    Writer writer = sourceFile.openWriter();
    try {
        writer.write("class " + className + " {");
        for (Element element : elements) {
            TypeElement typeElement = (TypeElement) element;
            String name = typeElement.getQualifiedName().toString();
            writer.write("    " + name + " get" + name + "() { return ");
            writer.write("new " + name + "()");
            writer.write("; }");
        }
        writer.write("}");
    } finally {
        writer.close();
    }
} catch (IOException e) {
    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className);
}
"""
    }
}
