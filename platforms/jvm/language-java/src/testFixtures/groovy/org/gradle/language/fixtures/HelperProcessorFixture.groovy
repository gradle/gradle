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
import org.gradle.test.fixtures.file.TestFile

import javax.tools.StandardLocation

/**
 * Generates a "Helper" class for each annotated type. The helper has a "getValue()" method that returns
 * a greeting. The greeting is composed of a message and a suffix. The message is compiled into a support
 * library. The suffix is compiled directly into the processor. This makes it easy to test different incremental
 * change scenarios. The message can also be provided with the -Amessage processor argument.
 */
@CompileStatic
class HelperProcessorFixture extends AnnotationProcessorFixture {
    String message = "greetings"
    boolean writeResources
    boolean withMultipleOriginatingElements
    String resourceLocation = StandardLocation.CLASS_OUTPUT.toString()
    private String suffix = ""

    HelperProcessorFixture() {
        super("Helper")
        declaredType = IncrementalAnnotationProcessorType.DYNAMIC
    }

    void setSuffix(String suffix) {
        this.suffix = suffix ? " " + suffix : ""
    }

    def writeSupportLibraryTo(TestFile projectDir) {
        // Some library class used by processor at runtime
        def utilClass = projectDir.file("src/main/java/${annotationName}Util.java")
        utilClass.text = """
            class HelperUtil {
                static String getValue() { return "${message}"; }
            }
"""
    }

    String getGeneratorCode() {
        def baseCode = """
for (Element element : elements) {
    TypeElement typeElement = (TypeElement) element;
    String className = typeElement.getSimpleName().toString() + "Helper";
    try {
        JavaFileObject sourceFile = filer.createSourceFile(className, ${withMultipleOriginatingElements ? "elements.toArray(new Element[0])" : "element"});
        Writer writer = sourceFile.openWriter();
        try {
            writer.write("class " + className + " {");
            writer.write("    String getValue() { return \\"");
            String messageFromOptions = options.get("message");
            if (messageFromOptions == null) {
                writer.write(HelperUtil.getValue() + "${suffix}");
            } else {
                writer.write(messageFromOptions);
            }
            writer.write("\\"; }");
            writer.write("}");
        } finally {
            writer.close();
        }
    } catch (IOException e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate source file " + className, element);
    }
"""
        def resourceCode = writeResources ? """
    try {
        FileObject resourceFile = filer.createResource($resourceLocation, "", className + "Resource.txt", ${withMultipleOriginatingElements ? "elements.toArray(new Element[0])" : "element"});
        Writer writer = resourceFile.openWriter();
        try {
            String messageFromOptions = options.get("message");
            if (messageFromOptions == null) {
                writer.write(HelperUtil.getValue() + "${suffix}");
            } else {
                writer.write(messageFromOptions);
            }
        } finally {
            writer.close();
        }
    } catch (Exception e) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Failed to write resource file .txt");
    }
}
""" : "}"

        return baseCode + resourceCode
    }

    @Override
    protected String getSupportedOptionsBlock() {
        if (declaredType == IncrementalAnnotationProcessorType.DYNAMIC) {
            """
            @Override
            public Set<String> getSupportedOptions() {
                return new HashSet<String>(Arrays.asList("message", "${IncrementalAnnotationProcessorType.ISOLATING.processorOption}"));
            }
            """
        } else {
            """
            @Override
            public Set<String> getSupportedOptions() {
                return Collections.singleton("message");
            }
            """
        }
    }
}
