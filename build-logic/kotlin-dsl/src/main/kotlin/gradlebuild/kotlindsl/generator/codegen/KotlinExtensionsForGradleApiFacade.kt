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

import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import java.io.File


/**
 * Reflectively calls generation functions in an isolated classloader.
 *
 * This is required so that our build uses the production code to generate extensions.
 *
 * The parameters of the called functions need to be from the JDK only in order to isolate
 * the Kotlin version.
 */
class KotlinExtensionsForGradleApiFacade(classPath: ClassPath) : AutoCloseable {

    private
    val loader: VisitableURLClassLoader

    init {
        loader = DefaultClassLoaderFactory().createIsolatedClassLoader(
            KotlinExtensionsForGradleApiFacade::class.java.simpleName,
            classPath
        ) as VisitableURLClassLoader
    }

    /**
     * Calls [org.gradle.kotlin.dsl.internal.sharedruntime.codegen.PluginIdExtensionsFacade.generate].
     */
    fun writeBuiltinPluginIdExtensionsTo(
        file: File,
        gradleJars: Iterable<File>,
        pluginDependenciesSpecQualifiedName: String,
        pluginDependencySpecQualifiedName: String,
    ) {
        invokeFacadeGenerateFunction(
            "org.gradle.kotlin.dsl.internal.sharedruntime.codegen.PluginIdExtensionsFacade",
            mapOf(
                "file" to file,
                "gradleJars" to gradleJars,
                "pluginDependenciesSpecQualifiedName" to pluginDependenciesSpecQualifiedName,
                "pluginDependencySpecQualifiedName" to pluginDependencySpecQualifiedName,
            )
        )
    }

    /**
     * Calls [org.gradle.kotlin.dsl.internal.sharedruntime.codegen.ApiExtensionGeneratorFacade.generate]
     */
    fun generateKotlinDslApiExtensionsSourceTo(
        asmLevel: Int,
        platformClassLoader: ClassLoader,
        incubatingAnnotationTypeDescriptor: String,
        outputDirectory: File,
        packageName: String,
        sourceFilesBaseName: String,
        hashTypeSourceName: java.util.function.Function<String, String>,
        classPath: List<File>,
        classPathDependencies: List<File>,
        apiSpec: java.util.function.Function<String, Boolean>,
        parameterNamesSupplier: java.util.function.Function<String, List<String>?>,
    ) {
        invokeFacadeGenerateFunction(
            "org.gradle.kotlin.dsl.internal.sharedruntime.codegen.ApiExtensionGeneratorFacade",
            mapOf(
                "asmLevel" to asmLevel,
                "platformClassLoader" to platformClassLoader,
                "incubatingAnnotationTypeDescriptor" to incubatingAnnotationTypeDescriptor,
                "outputDirectory" to outputDirectory,
                "packageName" to packageName,
                "sourceFilesBaseName" to sourceFilesBaseName,
                "hashTypeSourceName" to hashTypeSourceName,
                "classPath" to classPath,
                "classPathDependencies" to classPathDependencies,
                "apiSpec" to apiSpec,
                "parameterNamesSupplier" to parameterNamesSupplier,
            )
        )
    }

    private
    fun invokeFacadeGenerateFunction(className: String, parameters: Map<String, Any>) {
        val facadeClass = loader.loadClass(className)
        val facade = facadeClass.getConstructor().newInstance()
        val function = facadeClass.methods.single { it.name == "generate" }
        function.invoke(facade, parameters)
    }

    override fun close() {
        loader.close()
    }
}
