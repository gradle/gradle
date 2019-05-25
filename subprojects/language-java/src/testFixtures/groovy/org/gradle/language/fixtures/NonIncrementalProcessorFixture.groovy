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

/**
 * An annotation processor which does all the things that we don't support for
 * incremental compilation:
 *
 *  - generating files without originating elements
 *
 *  Useful for testing error reporting.
 */
@CompileStatic
class NonIncrementalProcessorFixture extends AnnotationProcessorFixture {

    private boolean providesNoOriginatingElements

    NonIncrementalProcessorFixture() {
        super("Thing")
    }

    NonIncrementalProcessorFixture providingNoOriginatingElements() {
        providesNoOriginatingElements = true
        this
    }

    String getGeneratorCode() {
        """
for (Element element : elements) {
    TypeElement typeElement = (TypeElement) element;
    String className = typeElement.getSimpleName().toString() + "Thing";
    try {
        JavaFileObject sourceFile = filer.createSourceFile(className${providesNoOriginatingElements ? "" : ", element"});
        Writer writer = sourceFile.openWriter();
        try {
            writer.write("class " + className + " {");
            writer.write("    String getValue() { return \\"");
            writer.write("Hello World");
            writer.write("\\"; }");
            writer.write("}");
        } finally {
            writer.close();
        }
    } catch (IOException e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className);
    }
}
"""
    }
}
