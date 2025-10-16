/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.apache.commons.io.FilenameUtils
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.zipTo
import org.gradle.util.internal.TextUtil
import org.junit.Before
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Suppress("FunctionName", "JUnitMixedFramework")
class SkipMetadataVersionCheckTest : AbstractKotlinIntegrationTest() {

    private companion object {
        private lateinit var jarFile: File

        @JvmStatic
        @BeforeAll
        fun setupClass(@TempDir tempDir: Path) {
            val classFile = ModifiedMetadataClassGenerator.generate(tempDir, "org.integ.test.util", "Printer", "print", 3, 0, 0)
            jarFile = File("$tempDir/printer.jar").also {
                zipTo(it, sequenceOf("org/integ/test/util/Printer.class" to classFile.readBytes()))
            }
        }
    }

    @Before
    fun setupBuild() {
        withDefaultSettings()

        withBuildScript(
            """
                import org.integ.test.util.Printer
    
                buildscript {
                  dependencies {
                    classpath(files("${TextUtil.normaliseFileSeparators(jarFile.canonicalPath)}"))
                  }
                }
    
                val printer = Printer()
                printer.print()
    
                println("Hello, from the buildfile!")
            """.trimIndent()
        )
    }

    @Test
    fun `no compilation error thrown when version check skipped`() {
        setupBuild()

        withFile(
            "gradle.properties", """
            org.gradle.kotlin.dsl.skipMetadataVersionCheck=true
        """.trimIndent()
        )

        build().apply {
            assertOutputContains("Hello, from the buildfile!")
            assertOutputContains("Hello, from Printer.print()!")
        }
    }

    @Test
    fun `comilation error thrown when version check NOT skipped`() {
        setupBuild()

        withFile(
            "gradle.properties", """
            org.gradle.kotlin.dsl.skipMetadataVersionCheck=false
        """.trimIndent()
        )

        buildAndFail().apply {
            assertHasErrorOutput("Class 'org.integ.test.util.Printer' was compiled with an incompatible version of Kotlin. The actual metadata version is 3.0.0, but the compiler version")
        }
    }

    object ModifiedMetadataClassGenerator {

        fun generate(directory: Path, packageName: String, className: String, functionName: String, vararg version: Int): File {
            val classFile = compileClass(directory, packageName, className, functionName)
            changeMetadataVersion(classFile, version)
            return classFile
        }

        private
        fun compileClass(directory: Path, packageName: String, className: String, functionName: String): File {
            val sourceFile = File("$directory/src/$className.kt")
            sourceFile.parentFile.mkdirs()
            sourceFile.appendText(
                """
                    package $packageName
                    
                    class $className {
                        fun $functionName() {
                            println("Hello, from $className.$functionName()!")
                        }
                    }
                """.trimIndent()
            )

            val binDir = File("$directory/bin")
            compileToDirectory(
                binDir,
                KotlinCompilerOptions(),
                "test",
                listOf(sourceFile),
                LoggerFactory.getLogger(SkipMetadataVersionCheckTest::class.java),
                emptyList()
            )

            val path = "${packageName.replace('.', '/')}/$className.class"
            return binDir.toPath().resolve(FilenameUtils.normalize(path)).toFile()
        }

        private fun changeMetadataVersion(classFile: File, newMetadataVersion: IntArray) {
            val classReader = ClassReader(classFile.readBytes())
            val classWriter = ClassWriter(classReader, 0)

            val metadataModifyingVisitor = MetadataModifyingVisitor(Opcodes.ASM9, classWriter, newMetadataVersion)
            classReader.accept(metadataModifyingVisitor, 0)

            val modifiedBytecode = classWriter.toByteArray()
            Files.write(classFile.toPath(), modifiedBytecode)
        }

        class MetadataModifyingVisitor(api: Int, classVisitor: ClassVisitor, val newVersion: IntArray) : ClassVisitor(api, classVisitor) {
            override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                val nextAnnotationVisitor = super.visitAnnotation(descriptor, visible)
                if (descriptor == "Lkotlin/Metadata;") {
                    return MetadataAnnotationVisitor(api, nextAnnotationVisitor, newVersion)
                }
                return nextAnnotationVisitor
            }
        }

        class MetadataAnnotationVisitor(api: Int, annotationVisitor: AnnotationVisitor?, val newVersion: IntArray) : AnnotationVisitor(api, annotationVisitor) {
            override fun visit(name: String, value: Any) {
                var newValue = value
                if (name == "mv" || name == "metadataVersion") {
                    newValue = newVersion
                }
                super.visit(name, newValue)
            }
        }
    }


}