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

package gradlebuild.integrationtests.action

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.configuration.DefaultImportsReader
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod


abstract class AnnotationGeneratorWorkAction : WorkAction<AnnotationGeneratorWorkAction.AnnotationGeneratorParameters> {

    internal
    interface AnnotationGeneratorParameters : WorkParameters {
        val packageName: Property<String>
        val destDir: DirectoryProperty
    }

    override fun execute() {
        val packageName = parameters.packageName.get()
        val sourceRootDirectory = parameters.destDir.get()
        val packageNamePath = packageName.replace(".", File.separator)
        val packageDirectory = sourceRootDirectory.file(packageNamePath).asFile
        if (!packageDirectory.exists() && !packageDirectory.mkdirs()) {
            throw IOException("Failed to create directory `$packageDirectory`")
        }
        val groovyAnnotationFile = File(packageDirectory, "GroovyBuildScriptLanguage.groovy")
        groovyAnnotationFile.writeText(generateGroovyAnnotation(packageName))
    }

    private
    object AnnotationGenerator {
        val ADDITIONAL_DEFAULT_IMPORTS = listOf(
            "javax.inject.Inject",
            "java.math.BigInteger",
            "java.math.BigDecimal"
        )
    }

    private
    fun generateGroovyAnnotation(packageName: String): String {
        @Suppress("UnnecessaryVariable")
        @Language("groovy")
        val annotationBody = """
        |/*
        | * Copyright 2020 the original author or authors.
        | *
        | * Licensed under the Apache License, Version 2.0 (the "License");
        | * you may not use this file except in compliance with the License.
        | * You may obtain a copy of the License at
        | *
        | *      http://www.apache.org/licenses/LICENSE-2.0
        | *
        | * Unless required by applicable law or agreed to in writing, software
        | * distributed under the License is distributed on an "AS IS" BASIS,
        | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        | * See the License for the specific language governing permissions and
        | * limitations under the License.
        | */
        |
        |package $packageName
        |
        |import org.intellij.lang.annotations.Language
        |
        |import java.lang.annotation.Retention
        |import java.lang.annotation.RetentionPolicy
        |import java.lang.annotation.Target
        |
        |import static java.lang.annotation.ElementType.ANNOTATION_TYPE
        |import static java.lang.annotation.ElementType.FIELD
        |import static java.lang.annotation.ElementType.LOCAL_VARIABLE
        |import static java.lang.annotation.ElementType.METHOD
        |import static java.lang.annotation.ElementType.PARAMETER
        |
        |@Retention(RetentionPolicy.CLASS)
        |@Target([METHOD, FIELD, PARAMETER, LOCAL_VARIABLE, ANNOTATION_TYPE])
        |@Language(value = "groovy", prefix = '''
        |${groovyImports().withTrimmableMargin()}
        |
        |${groovyProjectAccessors().withTrimmableMargin()}
        |''')
        |@interface GroovyBuildScriptLanguage {}
        |""".trimMargin() + '\n'
        return annotationBody
    }

    private
    fun String.withTrimmableMargin(): String =
        lines().joinToString(separator = "\n        |")

    private
    fun groovyImports(): String {
        val imports: List<String> =
            generateImportPackages()
                .map { "$it.*" } + "" +
                AnnotationGenerator.ADDITIONAL_DEFAULT_IMPORTS
        return imports.joinToString(separator = "\n") {
            if (it.isBlank()) "" else "import $it"
        }
    }

    private
    fun groovyProjectAccessors(): String {
        val objectMethods = Any::class.java.declaredMethods.toList()
        return Project::class
            .memberFunctions
            .asSequence()
            .mapNotNull { it.javaMethod }
            .filterNot { objectMethods.contains(it) }
            .map { it.toGroovyScriptString() }
            .sorted()
            .joinToString(separator = "\n")
    }

    private
    fun Method.toGroovyScriptString(): String {
        val cleanedTypeParameters =
            if (typeParameters.isNotEmpty()) {
                typeParameters.joinToString(prefix = "<", postfix = ">") { it.toCodeTypeString() }
            } else {
                ""
            }
        val cleanedGenericReturnType =
            genericReturnType.toCodeTypeString()
        return "$cleanedTypeParameters $cleanedGenericReturnType $name(${parameters.joinToString { "${it.parameterizedType.toCodeTypeString()} ${it.name}" }}) {}".trim()
    }

    private
    fun Type.toCodeTypeString(): String {
        if (this is Class<*> && this.isArray) {
            return this.componentType.toCodeTypeString() + "[]"
        }
        return toString()
            .replace("class ", "")
            .replace("interface ", "")
    }

    companion object {
        private
        const val RESOURCE = "/default-imports.txt"

        /**
         * Logic duplicated from [org.gradle.configuration.DefaultImportsReader].
         * Please keep this code in sync.
         */
        internal
        fun generateImportPackages(): List<String> {
            /*
             * The class that getResource called upon must be from within a jar that contains the import txt file.
             *
             * Note: Even though 'jump to declaration' in IJ will show this to be the 'DefaultImportsReader' from
             * the plugin Gradle distribution, this is only true at compile time.
             *
             * At runtime, the 'DefaultImportsReader' will be the version from the distribution actively being built/tested.
             */
            val clazz = DefaultImportsReader::class.java
            return clazz.getResource(RESOURCE)
                .readText()
                .split('\n')
                .filter { it.isNotBlank() }
                .map { line -> line.substring(7, line.length - 2) }
        }
    }
}
