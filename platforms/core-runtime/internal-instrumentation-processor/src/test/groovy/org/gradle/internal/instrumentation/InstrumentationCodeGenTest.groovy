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

package org.gradle.internal.instrumentation

import com.google.common.io.ByteStreams
import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import org.gradle.api.JavaVersion
import org.gradle.model.internal.asm.AsmConstants
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter
import org.gradle.internal.instrumentation.processor.ConfigurationCacheInstrumentationProcessor
import org.gradle.internal.jvm.Jvm
import org.gradle.model.internal.asm.MethodVisitorScope
import org.gradle.util.TestClassLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.JavaFileObject
import javax.tools.StandardLocation

import static com.google.testing.compile.Compiler.javac
import static org.junit.Assume.assumeTrue

abstract class InstrumentationCodeGenTest extends Specification {

    private static final List<String> COMPILE_OPTIONS = ["-Aorg.gradle.annotation.processing.instrumented.project=test-project"]

    @TempDir
    File tempFolder

    protected static String fqName(JavaFileObject javaFile) {
        return javaFile.name.replace("/", ".").replace(".java", "");
    }

    protected static JavaFileObject source(String source) {
        def packageGroup = (source =~ "\\s*package ([\\w.]+).*")
        String packageName = packageGroup.size() > 0 ? packageGroup[0][1] : ""
        String className = (source =~ /(?s).*?(?:class|interface|enum) ([\w$]+) .*/)[0][1]
        return packageName.isEmpty()
            ? JavaFileObjects.forSourceString(className, source)
            : JavaFileObjects.forSourceString("$packageName.$className", source);
    }

    protected static Compilation compile(JavaFileObject... fileObjects) {
        return getCompiler()
            .withProcessors(new ConfigurationCacheInstrumentationProcessor())
            .compile(fileObjects)
    }

    private static com.google.testing.compile.Compiler getCompiler() {
        assumeTrue("Java 20+ do not support --release=8", Jvm.current().javaVersion < JavaVersion.VERSION_20)
        if (Jvm.current().javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
            return javac().withOptions(COMPILE_OPTIONS + "--release=8")
        }
        return javac().withOptions(COMPILE_OPTIONS)
    }

    protected static String getDefaultPropertyUpgradeDeprecation(String className, String propertyName) {
        return "DeprecationLogger.deprecateProperty(" + className + ".class, \"" + propertyName + "\")\n" +
            ".withContext(\"Property was automatically upgraded to the lazy version.\")\n" +
            ".startingWithGradle9(\"this property is replaced with a lazy version\")\n" +
            ".undocumented()\n" +
            ".nagUser();";
    }

    protected <T> T instrumentRunnerJavaClass(String runnerClassName, String interceptorFactoryClassName, Compilation oldClassesCompilation, Compilation newClassesCompilation) {
        List<File> newClasspath = fromCompilationToClasspath(newClassesCompilation)
        JvmBytecodeCallInterceptor.Factory interceptorFactory = newInstance(interceptorFactoryClassName, newClasspath)
        JavaFileObject taskCallerFileObject = findClassJavaFileObject(runnerClassName, oldClassesCompilation)
        List<File> classpathWithInstrumentedClass = instrumentClass(taskCallerFileObject, interceptorFactory)
        return newInstance(runnerClassName, newClasspath + classpathWithInstrumentedClass)
    }

    protected List<File> fromCompilationToClasspath(Compilation compilation) {
        File classpath = new File(tempFolder, UUID.randomUUID().toString())
        compilation.generatedFiles()
            .findAll { it.kind == JavaFileObject.Kind.CLASS }
            .each {
                def classFile = new File(classpath, it.name.replace("/CLASS_OUTPUT/", ""))
                classFile.parentFile.mkdirs()
                classFile.bytes = ByteStreams.toByteArray(it.openInputStream())
            }
        return [classpath]
    }

    protected JavaFileObject findClassJavaFileObject(String className, Compilation compilation) {
        return compilation.generatedFile(StandardLocation.CLASS_OUTPUT, className.replace(".", "/") + ".class").get()
    }

    protected <T> T newInstance(String className, List<File> classpath) {
        Class<?> clazz = loadClass(className, classpath)
        return clazz.newInstance(new Object[0]) as T
    }

    protected Class<?> loadClass(String className, List<File> classpath) {
        TestClassLoader classLoader = new TestClassLoader(getClass().getClassLoader(), classpath)
        return classLoader.loadClass(className)
    }

    protected List<File> instrumentClass(JavaFileObject classToInstrument, JvmBytecodeCallInterceptor.Factory interceptorFactory) {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        TestInstrumentingVisitor classVisitor = new TestInstrumentingVisitor(interceptorFactory, classWriter)
        new ClassReader(ByteStreams.toByteArray(classToInstrument.openInputStream())).accept(classVisitor, 0)
        File classpath = new File(tempFolder, UUID.randomUUID().toString())
        File classFile = new File(classpath, classToInstrument.name.replace("/CLASS_OUTPUT/", ""))
        classFile.parentFile.mkdirs()
        classFile.bytes = classWriter.toByteArray()
        return [classpath]
    }

    private static class TestInstrumentingVisitor extends ClassVisitor {

        String className
        JvmBytecodeCallInterceptor.Factory interceptorFactory

        protected TestInstrumentingVisitor(JvmBytecodeCallInterceptor.Factory interceptorFactory, ClassVisitor delegate) {
            super(AsmConstants.ASM_LEVEL, delegate)
            this.interceptorFactory = interceptorFactory;
        }

        @Override
        void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces)
            this.className = name
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            def instrumentationMetadata = new InstrumentationMetadata() {
                @Override
                boolean isInstanceOf(String type, String superType) {
                    return type == superType
                }
            }
            return new MethodVisitorScope(methodVisitor) {
                @Override
                void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    def interceptor = interceptorFactory.create(instrumentationMetadata, BytecodeInterceptorFilter.ALL)
                    if (interceptor.visitMethodInsn(this, className, opcode, owner, methodName, methodDescriptor, isInterface, () -> {})) {
                        return
                    }
                    super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface)
                }
            }
        }
    }
}
