/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

/**
 * Generates the source for a non-Incap-enabled annotation processor that handles a
 * `@NonIncremental` annotation.  For classes annotated with `@NonIncremental`, the processor
 * generates a <class-name>NonIncremental.class.
 */
class NonIncrementalAnnotationProcessorFixture {

    String annotationFileName = 'src/main/java/NonIncremental.java'
    String processorClassName = 'src/main/java/NonIncapProcessor.java'
    String serviceFileName = 'src/main/resources/META-INF/javax.annotation.processing.Processor'

    def writeLibraryTo(TestFile projectDir) {
        projectDir.file('build.gradle').text = "apply plugin: 'java-library'"
        projectDir.file(processorClassName).text = nonIncapProcessorClass()
        projectDir.file(annotationFileName).text = "public @interface NonIncremental { }"
        projectDir.file(serviceFileName).text = "NonIncapProcessor"
    }

    private String nonIncapProcessorClass() {
        """
        import javax.annotation.processing.AbstractProcessor;
        import java.util.Set;
        import java.util.Collections;
        import java.io.Writer;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.util.Elements;
        import javax.annotation.processing.Filer;
        import javax.annotation.processing.Messager;
        import javax.lang.model.element.Element;
        import javax.lang.model.element.TypeElement;
        import javax.tools.JavaFileObject;
        import javax.annotation.processing.ProcessingEnvironment;
        import javax.annotation.processing.RoundEnvironment;
        import javax.tools.Diagnostic;

        public class NonIncapProcessor extends AbstractProcessor {
            private Elements elementUtils;
            private Filer filer;
            private Messager messager;

            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Collections.singleton(NonIncremental.class.getName());
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.latestSupported();
            }

            @Override
            public synchronized void init(ProcessingEnvironment processingEnv) {
                elementUtils = processingEnv.getElementUtils();
                filer = processingEnv.getFiler();
                messager = processingEnv.getMessager();
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                for (TypeElement annotation : annotations) {
                    if (annotation.getQualifiedName().toString().equals(NonIncremental.class.getName())) {
                        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                            TypeElement typeElement = (TypeElement) element;
                            String helperName = typeElement.getSimpleName().toString() + "NonIncremental";
                            try {
                                JavaFileObject sourceFile = filer.createSourceFile(helperName, element);
                                Writer writer = sourceFile.openWriter();
                                try {
                                    writer.write("class " + helperName + " {}");
                                } finally {
                                    writer.close();
                                }
                            } catch (Exception e) {
                                messager.printMessage(Diagnostic.Kind.ERROR,
                                   "Failed to generate source file " + helperName, element);
                            }
                        }
                    }
                }
                return true;
            }
        }
        """
    }
}
