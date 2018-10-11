/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmVariance
import kotlinx.metadata.KmVersionRequirementVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KmModuleVisitor


object KotlinMetadataPrintingVisitor {

    object ForPackage : KmPackageVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? {
            println("visitExtensions($type)")
            return object : JvmPackageExtensionVisitor() {
                override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
                    println("visitLocalDelegatedProperty($flags, $name, $getterFlags, $setterFlags)")
                    return super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
            println("visitProperty($flags, $name, $getterFlags, $setterFlags)")
            return ForProperty
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForProperty : KmPropertyVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            println("visitExtensions($type")
            return object : JvmPropertyExtensionVisitor() {
                override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
                    println("visit($fieldDesc, $getterDesc, $setterDesc)")
                    super.visit(fieldDesc, getterDesc, setterDesc)
                }

                override fun visitSyntheticMethodForAnnotations(desc: JvmMethodSignature?) {
                    println("visitSyntheticMethodForAnnotations($desc)")
                    super.visitSyntheticMethodForAnnotations(desc)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? {
            println("visitReceiverParameterType($flags)")
            return ForType
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? {
            println("visitReturnType($flags)")
            return ForType
        }

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
            println("visitVersionRequirement()")
            return super.visitVersionRequirement()
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForType : KmTypeVisitor() {

        override fun visitClass(name: ClassName) {
            println("visitClass($name)")
            super.visitClass(name)
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
            println("visitArgument($flags, $variance)")
            return ForType
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForModule : KmModuleVisitor() {
        override fun visitAnnotation(annotation: KmAnnotation) {
            println("visitAnnotation($annotation)")
            super.visitAnnotation(annotation)
        }

        override fun visitPackageParts(fqName: String, fileFacades: List<String>, multiFileClassParts: Map<String, String>) {
            println("visitPackageParts($fqName, $fileFacades, $multiFileClassParts")
            super.visitPackageParts(fqName, fileFacades, multiFileClassParts)
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }
}
