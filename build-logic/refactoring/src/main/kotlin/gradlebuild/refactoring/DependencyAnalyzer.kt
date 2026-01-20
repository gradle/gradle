/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.refactoring

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.io.File
import java.util.zip.ZipFile

/**
 * Result of the dependency analysis.
 *
 * @property projectClasses The set of classes that belong to the project and are part of the transitive dependency closure.
 * @property apiDependencyRoots The set of external JARs or directories that contain API dependencies of the [projectClasses].
 * @property implementationDependencyRoots The set of external JARs or directories that contain Implementation dependencies of the [projectClasses].
 */
data class AnalysisResult(
    val projectClasses: Set<String>,
    val apiDependencyRoots: Set<File>,
    val implementationDependencyRoots: Set<File>
)

private data class ClassDependencies(val api: Set<String>, val impl: Set<String>) {
    val all: Set<String> get() = api + impl
}

/**
 * Analyzes Java/Kotlin class files to determine their dependencies.
 *
 * This class uses the ASM library to parse bytecode and extract dependency information.
 * It performs a transitive dependency traversal starting from a given set of "split classes".
 */
class DependencyAnalyzer {

    /**
     * Performs a transitive dependency analysis.
     *
     * @param compiledClassesDirs The directories containing the compiled class files of the project.
     * @param compileClasspath The compile classpath of the project (JARs or directories).
     * @param splitClasses The set of Fully Qualified Class Names (FQCNs) to start the analysis from.
     * @return An [AnalysisResult] containing the reachable project classes and external dependency roots.
     */
    fun analyze(
        compiledClassesDirs: Iterable<File>,
        compileClasspath: Set<File>,
        splitClasses: Set<String>
    ): AnalysisResult {
        // 1. Index all available classes
        val classLocationMap = mutableMapOf<String, File>()

        // Index compiled classes
        for (dir in compiledClassesDirs) {
             if (dir.isDirectory) {
                dir.walk()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { file ->
                        parseClassName(file)?.let { className ->
                            classLocationMap[className] = dir
                        }
                    }
             }
        }

        // Index classpath
        for (path in compileClasspath) {
            if (path.isDirectory) {
                path.walk()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { file ->
                        parseClassName(file)?.let { className ->
                            classLocationMap.putIfAbsent(className, path)
                        }
                    }
            } else if (path.isFile && path.name.endsWith(".jar", ignoreCase = true)) {
                try {
                    ZipFile(path).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .forEach { entry ->
                                val className = entry.name.substringBeforeLast(".class").replace('/', '.')
                                classLocationMap.putIfAbsent(className, path)
                            }
                    }
                } catch (e: Exception) {
                    // Ignore bad jars
                }
            }
        }

        // 2. Transitive Traversal
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>(splitClasses)
        val projectClassesResult = mutableSetOf<String>()

        val apiExternalClasses = mutableSetOf<String>()
        val implExternalClasses = mutableSetOf<String>()

        // Cache for parsed dependencies to avoid re-parsing jars/files
        val dependencyCache = mutableMapOf<String, ClassDependencies>()

        val compiledClassesDirsSet = compiledClassesDirs.toSet()

        while (queue.isNotEmpty()) {
            val currentClass = queue.removeFirst()

            if (!visited.add(currentClass)) continue

            val location = classLocationMap[currentClass] ?: continue

            val isProjectClass = location in compiledClassesDirsSet
            if (isProjectClass) {
                projectClassesResult.add(currentClass)

                // Get dependencies
                val dependencies = dependencyCache.getOrPut(currentClass) {
                    parseDependencies(currentClass, location)
                }

                apiExternalClasses.addAll(dependencies.api)
                implExternalClasses.addAll(dependencies.impl)

                for (dep in dependencies.all) {
                    if (!visited.contains(dep)) {
                        queue.add(dep)
                    }
                }
            }
        }

        val apiRoots = mutableSetOf<File>()
        val implRoots = mutableSetOf<File>()

        // 1. Identify API roots
        apiExternalClasses.forEach { className ->
            val location = classLocationMap[className]
            if (location != null && location !in compiledClassesDirsSet) {
                apiRoots.add(location)
            }
        }

        // 2. Identify Implementation roots
        visited.forEach { className ->
            val location = classLocationMap[className]
            if (location != null && location !in compiledClassesDirsSet) {
                 implRoots.add(location)
            }
        }

        // 3. Ensure disjoint sets
        implRoots.removeAll(apiRoots)

        return AnalysisResult(projectClassesResult, apiRoots, implRoots)
    }

    private fun parseClassName(file: File): String? {
        return try {
            file.inputStream().use {
                val reader = ClassReader(it)
                reader.className.replace('/', '.')
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDependencies(className: String, location: File): ClassDependencies {
        val bytes = try {
            if (location.isDirectory) {
                val relativePath = className.replace('.', '/') + ".class"
                val classFile = File(location, relativePath)
                if (classFile.exists()) classFile.readBytes() else return ClassDependencies(emptySet(), emptySet())
            } else {
                ZipFile(location).use { zip ->
                    val entryName = className.replace('.', '/') + ".class"
                    val entry = zip.getEntry(entryName) ?: return ClassDependencies(emptySet(), emptySet())
                    zip.getInputStream(entry).readBytes()
                }
            }
        } catch (e: Exception) {
            return ClassDependencies(emptySet(), emptySet())
        }

        val reader = ClassReader(bytes)
        val visitor = ClassDependenciesVisitor()
        reader.accept(visitor, ClassReader.SKIP_FRAMES)

        // Ensure sets are disjoint and optimized
        val api = visitor.apiTypes
        val impl = visitor.implTypes - api
        return ClassDependencies(api, impl)
    }

    private class ClassDependenciesVisitor : ClassVisitor(Opcodes.ASM9) {
        val apiTypes = mutableSetOf<String>()
        val implTypes = mutableSetOf<String>()

        private fun isAccessible(access: Int): Boolean = (access and Opcodes.ACC_PRIVATE) == 0

        private fun maybeAddType(types: MutableSet<String>, type: Type) {
            when (type.sort) {
                Type.ARRAY -> maybeAddType(types, type.elementType)
                Type.OBJECT -> types.add(type.className)
                Type.METHOD -> {
                    maybeAddType(types, type.returnType)
                    type.argumentTypes.forEach { maybeAddType(types, it) }
                }
            }
        }

        private fun maybeAddType(types: MutableSet<String>, desc: String) {
            maybeAddType(types, Type.getType(desc))
        }

        private fun maybeAddTypeFromSignature(types: MutableSet<String>, signature: String?) {
            if (signature == null) return
            SignatureReader(signature).accept(object : SignatureVisitor(Opcodes.ASM9) {
                override fun visitClassType(name: String) {
                    types.add(Type.getObjectType(name).className)
                }
                override fun visitInnerClassType(name: String) {
                    types.add(Type.getObjectType(name).className)
                }
            })
        }

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            val types = if (isAccessible(access)) apiTypes else implTypes
            maybeAddTypeFromSignature(types, signature)
            if (superName != null) maybeAddType(types, Type.getObjectType(superName))
            interfaces?.forEach { maybeAddType(types, Type.getObjectType(it)) }
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor {
            val types = if (isAccessible(access)) apiTypes else implTypes
            maybeAddTypeFromSignature(types, signature)
            maybeAddType(types, descriptor)
            return object : FieldVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                    maybeAddType(types, descriptor)
                    return TypeCollectingAnnotationVisitor(types)
                }
                override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean): AnnotationVisitor {
                    maybeAddType(types, descriptor)
                    return TypeCollectingAnnotationVisitor(types)
                }
            }
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val types = if (isAccessible(access)) apiTypes else implTypes
            maybeAddTypeFromSignature(types, signature)

            val methodType = Type.getMethodType(descriptor)
            maybeAddType(types, methodType.returnType)
            methodType.argumentTypes.forEach { maybeAddType(types, it) }

            exceptions?.forEach { maybeAddType(types, Type.getObjectType(it)) }

            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitTypeInsn(opcode: Int, type: String) {
                    maybeAddType(implTypes, Type.getObjectType(type))
                }

                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                    maybeAddType(implTypes, Type.getObjectType(owner))
                    maybeAddType(implTypes, descriptor)
                }

                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean
                ) {
                    maybeAddType(implTypes, Type.getObjectType(owner))
                    val methodInsnType = Type.getMethodType(descriptor)
                    maybeAddType(implTypes, methodInsnType.returnType)
                    methodInsnType.argumentTypes.forEach { maybeAddType(implTypes, it) }
                }

                override fun visitLdcInsn(value: Any) {
                    if (value is Type) {
                        maybeAddType(implTypes, value)
                    } else if (value is Handle) {
                        maybeAddType(implTypes, Type.getObjectType(value.owner))
                        maybeAddType(implTypes, value.desc)
                    }
                }

                override fun visitLocalVariable(name: String, descriptor: String, signature: String?, start: Label?, end: Label?, index: Int) {
                     maybeAddType(implTypes, descriptor)
                     maybeAddTypeFromSignature(implTypes, signature)
                }

                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                    maybeAddType(types, descriptor)
                    return TypeCollectingAnnotationVisitor(types)
                }

                override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): AnnotationVisitor {
                    maybeAddType(types, descriptor)
                    return TypeCollectingAnnotationVisitor(types)
                }

                override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String, visible: Boolean): AnnotationVisitor {
                    maybeAddType(types, descriptor)
                    return TypeCollectingAnnotationVisitor(types)
                }

                override fun visitInvokeDynamicInsn(
                    name: String,
                    descriptor: String,
                    bootstrapMethodHandle: Handle,
                    vararg bootstrapMethodArguments: Any
                ) {
                    maybeAddType(implTypes, descriptor)

                    maybeAddType(implTypes, Type.getObjectType(bootstrapMethodHandle.owner))
                    maybeAddType(implTypes, bootstrapMethodHandle.desc)

                    for (arg in bootstrapMethodArguments) {
                        if (arg is Type) {
                            maybeAddType(implTypes, arg)
                        } else if (arg is Handle) {
                            maybeAddType(implTypes, Type.getObjectType(arg.owner))
                            maybeAddType(implTypes, arg.desc)
                        }
                    }
                }
            }
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
            maybeAddType(apiTypes, descriptor)
            return TypeCollectingAnnotationVisitor(apiTypes)
        }

        private inner class TypeCollectingAnnotationVisitor(val types: MutableSet<String>) : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String?, value: Any?) {
                if (value is Type) maybeAddType(types, value)
            }
            override fun visitEnum(name: String?, descriptor: String, value: String?) {
                maybeAddType(types, descriptor)
            }
            override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
                maybeAddType(types, descriptor)
                return this
            }
            override fun visitArray(name: String?): AnnotationVisitor = this
        }
    }
}
