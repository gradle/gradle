/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.filter

import org.gradle.testretry.internal.testsreader.TestsReader
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import javax.tools.JavaCompiler
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import org.jspecify.annotations.Nullable

class AnnotationInspectorImplTest extends Specification {

    @Rule
    TemporaryFolder dir = new TemporaryFolder()

    AnnotationInspector inspector

    def "finds annotations"() {
        given:
        nonInheritedAnnotation("AN1")
        nonInheritedAnnotation("AN2")
        nonInheritedAnnotation("AN3")
        inheritedAnnotation("AI1")
        inheritedAnnotation("AI2")
        inheritedAnnotation("AI3")

        classWithAnnotations("NoAnnotationBase", null)
        classWithAnnotations("IncludeAnnotationBase", null, "AN1", "AI1")
        classWithAnnotations("IncludeAnnotationChild", "IncludeAnnotationBase", "AN2", "AI2")
        classWithAnnotations("IncludeAnnotationChildChild", "IncludeAnnotationChild")
        classWithAnnotations("IncludeAnnotationChildChildChild", "IncludeAnnotationChildChild")

        expect:
        annotationsOf("NoAnnotationBase").empty
        annotationsOf("NotExist").empty
        annotationsOf("IncludeAnnotationBase") == ["AI1", "AN1"]
        annotationsOf("IncludeAnnotationChild") == ["AI1", "AI2", "AN2"]
        annotationsOf("IncludeAnnotationChildChild") == ["AI1", "AI2"]
        annotationsOf("IncludeAnnotationChildChildChild") == ["AI1", "AI2"]
    }

    List<String> annotationsOf(String className) {
        if (inspector == null) {
            inspector = inspector()
        }
        inspector.getClassAnnotations(className).toList().sort()
    }

    File file(String path) {
        def file = new File(dir.root, path)
        assert file.parentFile.mkdirs() || file.parentFile.directory
        assert file.createNewFile() || file.file
        file
    }

    AnnotationInspector inspector() {
        def classesDir = new File(dir.root, "build/classes/java/main")
        classesDir.mkdirs()
        compileJavaSources(new File(dir.root, "src/main/java"), classesDir)
        def reader = new TestsReader([classesDir].toSet(), [])
        new AnnotationInspectorImpl(reader)
    }

    private static void compileJavaSources(File sourceDir, File outputDir) {
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        def fileManager = compiler.getStandardFileManager(null, null, null)
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, [outputDir])
            def sourceFiles = []
            sourceDir.eachFileRecurse { if (it.name.endsWith(".java")) sourceFiles << it }
            def javaFileObjects = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
            def task = compiler.getTask(null, fileManager, null, null, null, javaFileObjects)
            assert task.call(): "javac failed to compile ${sourceFiles*.name}"
        } finally {
            fileManager.close()
        }
    }

    private void nonInheritedAnnotation(String name) {
        file("src/main/java/${name}.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            public @interface $name { }
        """
    }

    private void inheritedAnnotation(String name) {
        file("src/main/java/${name}.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            @java.lang.annotation.Inherited
            public @interface $name { }
        """
    }

    private void classWithAnnotations(String name, @Nullable String superClass, String... annotations) {
        file("src/main/java/${name}.java") << """
            ${annotations.collect { "@$it" }.join("\n")}
            class $name ${superClass ? " extends $superClass" : ""} {}
        """
    }
}
