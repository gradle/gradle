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

package gradlebuild.instrumentation.classpostprocessing

import org.gradle.api.GradleException
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File


internal
fun postProcessClasses(
    classesDirectory: File,
    postProcessors: Iterable<ClassPostProcessor>
) {
    postProcessors.forEach { postProcessor ->
        postProcessor.classNamesToProcess.forEach { className ->
            val classFile = classFileByName(classesDirectory, className)
            if (!classFile.isFile) {
                throw GradleException("Class file that needs processing is missing: $classFile")
            }
            val classReader = ClassReader(classFile.readBytes())
            val classWriter = ClassWriter(classReader, 0)
            val classVisitor = postProcessor.classVisitorForClass(className, classWriter)
            classReader.accept(classVisitor, 0)
            val outBytes = classWriter.toByteArray()
            classFile.writeBytes(outBytes)
        }
    }
}


private
fun classFileByName(classesDirectory: File, className: String): File {
    val path = className.replace(".", "/") + ".class"
    return classesDirectory.resolve(path)
}
