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

import groovy.transform.CompileStatic
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.test.fixtures.file.TestFile

/**
 * Base class for all annotation processor test fixtures. Each processor listens to a single annotation.
 * It provides the basic scaffolding, like fields for the filer, element utils and messager as well as
 * finding the annotated elements. Subclasses only need to provide the processing logic given those elements.
 *
 * The declared type of the processor can be overwritten to test various error cases, e.g. a processor that
 * declares itself as incremental, but doesn't honor that contract.
 */
@CompileStatic
class AnnotationProcessorFixture {
    protected final String annotationName
    protected final String supportedAnnotationTypes;
    protected final String annotationPackageName
    protected final String fqAnnotationName
    IncrementalAnnotationProcessorType declaredType

    AnnotationProcessorFixture(String annotationName) {
        this('', annotationName)
    }

    AnnotationProcessorFixture(String annotationPackageName, String annotationName, boolean processAllCode = false) {
        this.annotationName = annotationName
        this.supportedAnnotationTypes = processAllCode ? '"*"' : "${annotationName}.class.getName()"
        this.annotationPackageName = annotationPackageName
        this.fqAnnotationName = annotationPackageName.empty ? annotationName : "${annotationPackageName}.${annotationName}"
    }

    final void writeApiTo(TestFile projectDir) {
        def packagePathPrefix = annotationPackageName.empty ? '' : "${annotationPackageName.replace('.', '/')}/"
        def packageStatement = annotationPackageName.empty ? '' : "package ${annotationPackageName};"
        // Annotation handled by processor
        projectDir.file("src/main/java/${packagePathPrefix}${annotationName}.java").text = """
            ${packageStatement}
            public @interface $annotationName {
            }
"""
    }

    AnnotationProcessorFixture withDeclaredType(IncrementalAnnotationProcessorType type) {
        declaredType = type
        this
    }

    def writeSupportLibraryTo(TestFile projectDir) {
        //no support library by default
    }

    String getDependenciesBlock() {
        ""
    }

    String getRepositoriesBlock() {
        ""
    }

    final void writeAnnotationProcessorTo(TestFile projectDir) {
        // The annotation processor
        projectDir.file("src/main/java/${annotationName}Processor.java").text = """
            import java.io.*;
            import java.util.*;
            import javax.annotation.processing.*;
            import javax.lang.model.*;
            import javax.lang.model.element.*;
            import javax.lang.model.util.*;
            import javax.tools.*;
            ${annotationPackageName.empty ? '' : "import ${fqAnnotationName};"}

            import static javax.tools.StandardLocation.*;

            @SupportedOptions({ "message" })
            public class ${annotationName}Processor extends AbstractProcessor {
                private Map<String, String> options;
                private Elements elementUtils;
                private Filer filer;
                private Messager messager;

                ${membersBlock}

                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    return Collections.singleton(${supportedAnnotationTypes});
                }

                ${supportedOptionsBlock}

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public synchronized void init(ProcessingEnvironment processingEnv) {
                    elementUtils = processingEnv.getElementUtils();
                    filer = processingEnv.getFiler();
                    messager = processingEnv.getMessager();
                    options = processingEnv.getOptions();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    Set<Element> elements = new HashSet<>();
                    for (TypeElement annotation : annotations) {
                         elements.addAll(roundEnv.getElementsAnnotatedWith(annotation));
                    }
                    ${generatorCode}
                    return true;
                }
            }
"""
        projectDir.file("src/main/resources/$AnnotationProcessorDetector.PROCESSOR_DECLARATION") << "${annotationName}Processor\n"
        if (declaredType) {
            projectDir.file("src/main/resources/$AnnotationProcessorDetector.INCREMENTAL_PROCESSOR_DECLARATION") << "${annotationName}Processor,$declaredType\n"
        }
        def deps = dependenciesBlock
        if (deps) {
            projectDir.file("build.gradle") << """
            dependencies {
                $deps
            }
            """
        }
        def repos = repositoriesBlock
        if (repos) {
            projectDir.file("build.gradle") << """
            repositories {
                $repos
            }
            """
        }
    }

    protected String getGeneratorCode() {
        ""
    }
    protected String getSupportedOptionsBlock() {
        ""
    }
    protected String getMembersBlock() {
        ""
    }
}
