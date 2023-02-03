/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.test.fixtures.jpms

import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaCompiler
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ModuleJarFixture {

    private enum JarKind {
        LIB,
        MODULE,
        AUTO_MODULE
    }

    static byte[] traditionalJar(String name) {
        jar(name, '', JarKind.LIB)
    }

    static byte[] moduleJar(String name, String moduleInfoStatements = '') {
        jar(name, moduleInfoStatements, JarKind.MODULE)
    }

    static byte[] autoModuleJar(String name, String moduleInfoStatements = '') {
        jar(name, moduleInfoStatements, JarKind.AUTO_MODULE)
    }

    private static byte[] jar(String name, String moduleInfoStatements, JarKind kind) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>()

        def publicClassName = name.split('\\.').last().capitalize() + 'Class'
        def internalClassName = publicClassName + 'Internal'

        JavaFileObject publicClass = new JavaSourceFromString(publicClassName, """
            package $name;

            public class $publicClassName { }
        """)
        JavaFileObject internalClass = new JavaSourceFromString(internalClassName, """
            package ${name}.internal;

            public class $internalClassName { }
        """)
        def classesToCompile = [publicClass, internalClass]
        if (kind == JarKind.MODULE) {
            JavaFileObject moduleInfo = new JavaSourceFromString('module-info', """
                module $name {
                    exports $name;
                    $moduleInfoStatements
                }
            """)
            classesToCompile += moduleInfo
        }

        def manifest = ['Manifest-Version: 1.0', "Implementation-Title: $name", 'Implementation-Version: 1.0']
        if (kind == JarKind.AUTO_MODULE) {
            manifest += "Automatic-Module-Name: $name"
        }

        final FileManager fileManager = new FileManager(compiler.getStandardFileManager(null, null, null))
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, classesToCompile)

        if (task.call()) {
            def result = new ByteArrayOutputStream()
            def target = new JarOutputStream(result)
            target.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"))
            target.write((manifest.join('\n') + '\n').bytes)
            fileManager.output.each { classFile ->
                def entryName = classFile.name.replace(".", "/") + ".class"
                target.putNextEntry(new JarEntry(entryName))
                target.write(classFile.outputStream.toByteArray())
                target.closeEntry()
            }
            target.close()
            return result.toByteArray()
        } else {
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                println(diagnostic.source)
                println(diagnostic.getMessage(null))
            }
            throw new RuntimeException('Failure compiling test fixture')
        }
    }

    private static class FileManager
        extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private List<ClassFile> output = new ArrayList<>()

        FileManager(StandardJavaFileManager target) {
            super(target)
        }

        @Override
        JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            final ClassFile file = new ClassFile(new URI(null, null, className, null))
            output.add(file)
            return file
        }
    }

    private static class ClassFile extends SimpleJavaFileObject {
        private ByteArrayOutputStream outputStream

        private ClassFile(URI uri) {
            super(uri, Kind.CLASS)
        }

        @Override
        String getName() { return super.uri.getRawSchemeSpecificPart() }

        @Override
        OutputStream openOutputStream() throws IOException {
            return outputStream = new ByteArrayOutputStream()
        }
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension),Kind.SOURCE)
            this.code = code
        }

        @Override
        CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code
        }
    }
}

