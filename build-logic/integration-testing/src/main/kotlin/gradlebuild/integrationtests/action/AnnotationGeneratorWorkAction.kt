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
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod


class AnnotationGeneratorWorkAction {

    fun execute(
        packageName: String,
        sourceRootDirectory: File,
        defaultImportPackages: List<String>
    ) {
        val packageNamePath = packageName.replace(".", File.separator)
        val packageDirectory = File(sourceRootDirectory, packageNamePath)
        if (!packageDirectory.exists() && !packageDirectory.mkdirs()) {
            throw IOException("Failed to create directory `$packageDirectory`")
        }

        writeAnnotationFile(packageDirectory, packageName, "GroovyBuildScriptLanguage", defaultImportPackages) {
            groovyReceiverAccessors<Project>() + "\n" + PLUGINS_BLOCK_SIGNATURE
        }

        writeAnnotationFile(packageDirectory, packageName, "GroovySettingsScriptLanguage", defaultImportPackages) {
            groovyReceiverAccessors<Settings>() + "\n" + PLUGINS_BLOCK_SIGNATURE
        }

        writeAnnotationFile(packageDirectory, packageName, "GroovyInitScriptLanguage", defaultImportPackages) {
            groovyReceiverAccessors<Gradle>()
        }
    }

    private fun writeAnnotationFile(packageDirectory: File, packageName: String, name: String, defaultImportPackages: List<String>, scriptReceiverAccessors: () -> String) {
        val groovyBuildScriptAnnotationFile = File(packageDirectory, "$name.groovy")
        groovyBuildScriptAnnotationFile.writeText(generateGroovyAnnotation(packageName, name, defaultImportPackages, scriptReceiverAccessors))
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
    fun generateGroovyAnnotation(packageName: String, name: String, defaultImportPackages: List<String>, scriptReceiverAccessors: () -> String): String {
        @Suppress("GrPackage")
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
        |${groovyImports(defaultImportPackages).withTrimmableMargin()}
        |
        |${scriptReceiverAccessors().withTrimmableMargin()}
        |''')
        |@interface $name {}
        |""".trimMargin() + '\n'
        return annotationBody
    }

    private
    fun String.withTrimmableMargin(): String =
        lines().joinToString(separator = "\n        |")

    private
    fun groovyImports(defaultImportPackages: List<String>): String {
        val imports: List<String> =
            defaultImportPackages
                .map { "$it.*" } + "" +
                AnnotationGenerator.ADDITIONAL_DEFAULT_IMPORTS
        return imports.joinToString(separator = "\n") {
            if (it.isBlank()) "" else "import $it"
        }
    }

    private
    inline fun <reified Receiver> groovyReceiverAccessors(): String {
        return groovyReceiverAccessors(Receiver::class)
    }

    private
    fun groovyReceiverAccessors(receiverType: KClass<*>): String {
        val objectMethods = Any::class.java.declaredMethods.toList()
        return receiverType
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
        const val PLUGINS_BLOCK_SIGNATURE =
            "void plugins(@groovy.transform.stc.ClosureParams(value = groovy.transform.stc.SimpleType.class, options = 'org.gradle.plugin.use.PluginDependenciesSpec') Closure configuration) {}"

    }

}
