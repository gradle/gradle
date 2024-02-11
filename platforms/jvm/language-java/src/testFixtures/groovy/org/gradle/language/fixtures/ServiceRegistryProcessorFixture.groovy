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
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType

import javax.tools.StandardLocation

/**
 * A processor that collects all types annotated with the @Service annotation
 * and generates a single ServiceRegistryResource.txt file from them.
 * Also creates a class called ServiceRegistry which does not reference any of the
 * other classes, but could simply serve as an entry point for reading the resource file.
 */
@CompileStatic
class ServiceRegistryProcessorFixture extends AnnotationProcessorFixture {
    String resourceLocation = StandardLocation.CLASS_OUTPUT.toString()

    ServiceRegistryProcessorFixture() {
        super("Service")
        declaredType = IncrementalAnnotationProcessorType.AGGREGATING
    }

    @Override
    protected String getMembersBlock() {
        """
            int round;
            Set<Element> allElements = new HashSet<Element>();
        """
    }

    String getGeneratorCode() {
        return """
            try {
                if (round == 0) {
                    JavaFileObject javaFile = filer.createSourceFile("ServiceRegistry", new Element[0]);
                    Writer writer = javaFile.openWriter();
                    try {
                        writer.write("class ServiceRegistry {}");
                    } finally {
                        writer.close();
                    }
                }
                if (roundEnv.processingOver()) {
                    FileObject resourceFile = filer.createResource($resourceLocation, "", "ServiceRegistryResource.txt", allElements.toArray(new Element[0]));
                    Writer writer = resourceFile.openWriter();
                    try {
                        for (Element element : allElements) {
                            TypeElement typeElement = (TypeElement) element;
                            String name = typeElement.getQualifiedName().toString();
                            writer.write(name);
                            writer.write('\\n');
                        }
                    } finally {
                        writer.close();
                    }
                } else {
                    allElements.addAll(elements);
                }
                round++;
            } catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process : " + e.getMessage());
            }
        """
    }
}
