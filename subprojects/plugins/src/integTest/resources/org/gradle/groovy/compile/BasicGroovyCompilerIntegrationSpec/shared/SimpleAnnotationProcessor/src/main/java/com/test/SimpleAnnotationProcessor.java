/*
 * Copyright 2015 the original author or authors.
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
package com.test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("com.test.SimpleAnnotation")
public class SimpleAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        for (final Element classElement : roundEnv.getElementsAnnotatedWith(SimpleAnnotation.class)) {
            final String className = String.format("%s$$Generated", classElement.getSimpleName().toString());

            Writer writer = null;
            try {
                final JavaFileObject file = processingEnv.getFiler().createSourceFile(className);

                writer = new BufferedWriter(file.openWriter());
                writer.append(String.format("public class %s {\n", className));
                writer.append("}");
            } catch (final IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
