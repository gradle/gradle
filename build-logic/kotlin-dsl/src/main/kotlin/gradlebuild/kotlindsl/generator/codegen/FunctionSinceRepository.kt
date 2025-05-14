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

package gradlebuild.kotlindsl.generator.codegen

import com.thoughtworks.qdox.JavaProjectBuilder
import com.thoughtworks.qdox.library.OrderedClassLibraryBuilder
import com.thoughtworks.qdox.model.JavaAnnotatedElement
import com.thoughtworks.qdox.model.JavaClass
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath
import java.io.File
import java.io.StringReader
import java.net.URLClassLoader


/**
 * Provides `@since` javadoc tag value from sources.
 */
class FunctionSinceRepository(classPath: Set<File>, sourcePath: Set<File>) : AutoCloseable {

    // https://github.com/paul-hammant/qdox/issues/182
    private
    val filesWithUnsupportedAnnotations = listOf(
        "Transformer.java",
        "Provider.java",
        "ListProperty.java",
        "MapProperty.java",
        "Property.java",
        "SetProperty.java",
        "FileComparisonTestAssertionFailure.java",
        "TestFailureDetails.java",
    )

    private
    val loader: URLClassLoader =
        isolatedClassLoaderFor(classPath)

    private
    val builder: JavaProjectBuilder =
        javaProjectBuilderFor(loader, sourcePath)

    fun since(functionSignature: String): String? {

        val javaFunction = parsedJavaFunctionOf(functionSignature)

        val matchingType = builder.allSourceClasses
            .singleOrNull { javaFunction.typeName == it.binaryName }
            ?: throw IllegalArgumentException("Class for function '$functionSignature' not found in since repository! See FunctionSinceRepository.kt")

        val matchingFunction = javaFunction
            .run { matchingType.getMethodsBySignature(name, parameterTypes, false, isVararg) }
            .singleOrNull()
            ?: throw IllegalArgumentException("Function '$functionSignature' not found in @since repository! See FunctionSinceRepository.kt")

        return matchingFunction.since ?: matchingType.since
    }

    private
    val JavaProjectBuilder.allSourceClasses
        get() = sources.asSequence()
            .flatMap { it.classes }
            .flatMap { sequenceOf(it) + it.nestedClasses }

    private
    fun parsedJavaFunctionOf(functionSignature: String): JavaFunction<JavaClass> =
        javaFunctionOf(functionSignature).map {
            builder.getClassByName(it)
        }

    private
    val JavaAnnotatedElement.since: String?
        get() = getTagByName("since")?.value

    private
    fun isolatedClassLoaderFor(classPath: Set<File>): URLClassLoader =
        DefaultClassLoaderFactory().createIsolatedClassLoader("FunctionsSinceRepository", DefaultClassPath.of(classPath)) as URLClassLoader

    private
    fun javaProjectBuilderFor(loader: ClassLoader, sourcePath: Set<File>): JavaProjectBuilder =
        JavaProjectBuilder(OrderedClassLibraryBuilder().apply {
            appendClassLoader(loader)
        }).apply {
            sourcePath.filter { it.extension == "java" }
                .forEach { sourceFile ->
                    if (sourceFile.name in filesWithUnsupportedAnnotations) {
                        // This is a hack.
                        //
                        // qdox doesn't understand type-annotations placed in generic type parameters, arrays or varargs
                        // The only place we use this is with Nullable, so this hackily removes the annotation when
                        // the source file is processed by qdox.
                        //
                        // https://github.com/paul-hammant/qdox/issues/182
                        val filteredSourceCode =
                            sourceFile.bufferedReader().readText()
                                .replace("extends @Nullable ", "extends ")
                                .replace("@Nullable []", "[]")
                                .replace("@Nullable ...", "...")
                        addSource(StringReader(filteredSourceCode))
                    } else {
                        addSource(sourceFile)
                    }
                }
        }

    override fun close() =
        loader.close()
}


/**
 * Extract a [JavaFunction] helper object from a function signature string.
 */
internal
fun javaFunctionOf(functionSignature: String): JavaFunction<String> =
    JavaFunction(
        typeName = functionSignature.split('(')[0].dropLastWhile { it != '.' }.dropLast(1),
        name = functionSignature.split('(')[0].takeLastWhile { it != '.' },
        parameterTypes = functionSignature.split('(')[1].dropLast(1).split(",").map { it.trim() }.let { paramStrings ->
            paramStrings.mapIndexed { idx: Int, paramString: String ->
                if (idx != paramStrings.size - 1) paramString
                else paramString.replace("[]", "")
            }
        },
        isVararg = functionSignature.dropLast(1).endsWith("[]")
    )


internal
data class JavaFunction<out ParameterType>(
    val typeName: String,
    val name: String,
    val parameterTypes: List<ParameterType>,
    val isVararg: Boolean,
) {
    fun <T> map(transform: (ParameterType) -> T): JavaFunction<T> =
        JavaFunction(
            typeName, name, parameterTypes.map(transform), isVararg
        )
}
