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
 * Generates the source for an Incap-enabled annotation processor that handles an
 * `@Incremental` annotation.  For classes annotated with `@Incremental`, the processor
 * generates a <class-name>Incremental.class.
 */
class IncrementalAnnotationProcessorFixture {

    // TODO:  Can we query this somehow, from our actual project dependencies?
    final String INCAP_VERSION = 'org.gradle.incap:incap-ap-client:0.0.7-SNAPSHOT'

    final String annotationFileName = 'src/main/java/Incremental.java'
    final String processorClassName = 'src/main/java/IncapProcessor.java'
    final String serviceFileName = 'src/main/resources/META-INF/services/javax.annotation.processing.Processor'
    public final String INCAP_TAG_FILE_NAME = 'src/main/resources/META-INF/org.gradle.incap'

    def writeLibraryTo(TestFile projectDir) {
        writeBuildFile(projectDir)
        projectDir.file(annotationFileName).text = "public @interface Incremental { }"
        projectDir.file(processorClassName).text = incapProcessorClass()
        projectDir.file(serviceFileName).text = "IncapProcessor"
        writeIncapSupportLevel(projectDir)
    }

    protected void writeIncapSupportLevel(TestFile projectDir) {
        projectDir.file(INCAP_TAG_FILE_NAME).text = "simple"
    }

    private void writeBuildFile(TestFile projectDir) {
        projectDir.file('build.gradle').text = """
        apply plugin: 'java-library'

        repositories {
          maven {
            url 'https://dl.bintray.com/incap/incap'
            url 'https://repo.gradle.org/gradle/libs-snapshots'
          }
        }

        dependencies {
          api '$INCAP_VERSION'
        }
        """
    }

    private String incapProcessorClass() {
        """
        import java.util.Set;
        import java.util.Collections;
        import java.io.Writer;
        import javax.lang.model.SourceVersion;
        import javax.lang.model.util.Elements;
        import javax.annotation.processing.Filer;
        import javax.annotation.processing.Messager;
        import javax.lang.model.element.Element;
        import javax.lang.model.element.TypeElement;
        import javax.lang.model.element.ExecutableElement;
        import javax.lang.model.util.ElementFilter;
        import javax.tools.JavaFileObject;
        import javax.annotation.processing.ProcessingEnvironment;
        import javax.annotation.processing.RoundEnvironment;
        import javax.tools.Diagnostic;
        import org.gradle.incap.BaseIncrementalAnnotationProcessor;

        public class IncapProcessor extends BaseIncrementalAnnotationProcessor {
            private Elements elementUtils;
            private Filer filer;
            private Messager messager;

            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Collections.singleton(Incremental.class.getName());
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.latestSupported();
            }

            @Override
            public synchronized void init(ProcessingEnvironment processingEnv) {
                elementUtils = processingEnv.getElementUtils();
                messager = processingEnv.getMessager();
                super.init(processingEnv);
            }

            @Override
            public boolean incrementalProcess(Set<? extends TypeElement> elements, RoundEnvironment roundEnv) {
                filer = incrementalProcessingEnvironment.getFiler();
                for (TypeElement annotation : elements) {
                    if (annotation.getQualifiedName().toString().equals(Incremental.class.getName())) {
                        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                            TypeElement typeElement = (TypeElement) element;
                            String helperName = typeElement.getSimpleName().toString() + "Incremental";
                            try {
                                JavaFileObject sourceFile = filer.createSourceFile(helperName, element);
                                Writer writer = sourceFile.openWriter();
                                try {
                                    writer.write("class " + helperName + " {\\n");
                                    writer.write("    String getValue() { return \\"incremental\\"; }\\n");
                                    writeTargetMethods(writer, typeElement);
                                    writer.write("\\n}\\n");
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

            private void writeTargetMethods(Writer writer, TypeElement root) throws Exception {
                for (ExecutableElement method : ElementFilter.methodsIn(root.getEnclosedElements())) {
                    // foo() -> generatedFoo()
                    String name = method.getSimpleName().toString();
                    String capName = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    writer.write("    public void generated" + capName + "() {}\\n");
                }
            }
        }
        """
    }
}
