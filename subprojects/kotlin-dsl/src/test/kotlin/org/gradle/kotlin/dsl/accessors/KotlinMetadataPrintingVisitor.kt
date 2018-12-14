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
import kotlinx.metadata.KmFunctionExtensionVisitor
import kotlinx.metadata.KmFunctionVisitor
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeExtensionVisitor
import kotlinx.metadata.KmTypeParameterExtensionVisitor
import kotlinx.metadata.KmTypeParameterVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmValueParameterVisitor
import kotlinx.metadata.KmVariance
import kotlinx.metadata.KmVersionRequirementVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.JvmTypeExtensionVisitor
import kotlinx.metadata.jvm.JvmTypeParameterExtensionVisitor
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

        override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
            println("visitFunction($flags, $name)")
            return ForFunction
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForFunction : KmFunctionVisitor() {

        override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? {
            println("visitTypeParameter($flags, $name, $id, $variance)")
            return object : KmTypeParameterVisitor() {
                override fun visitUpperBound(flags: Flags): KmTypeVisitor? {
                    println("visitUpperBound($flags)")
                    return ForType
                }

                override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor? {
                    println("visitExtensions($type)")
                    return object : JvmTypeParameterExtensionVisitor() {
                        override fun visitAnnotation(annotation: KmAnnotation) {
                            println("visitAnnotation($annotation)")
                            super.visitAnnotation(annotation)
                        }
                        override fun visitEnd() {
                            println("visitEnd()")
                            super.visitEnd()
                        }
                    }
                }
                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
            println("visitExtensions($type)")
            return object : JvmFunctionExtensionVisitor() {
                override fun visit(desc: JvmMethodSignature?) {
                    println("visit($desc)")
                    super.visit(desc)
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

        override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? {
            println("visitValueParameter($flags, $name)")
            return object : KmValueParameterVisitor() {
                override fun visitType(flags: Flags): KmTypeVisitor? {
                    println("visitType($flags)")
                    return ForType
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? {
            println("visitReturnType($flags)")
            return ForType
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

        override fun visitTypeAlias(name: ClassName) {
            println("visitTypeAlias($name)")
            super.visitTypeAlias(name)
        }

        override fun visitStarProjection() {
            println("visitStarProjection()")
            super.visitStarProjection()
        }

        override fun visitOuterType(flags: Flags): KmTypeVisitor? {
            println("visitOuterType($flags)")
            return ForType
        }

        override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? {
            println("visitAbbreviatedType($flags)")
            return ForType
        }

        override fun visitTypeParameter(id: Int) {
            println("visitTypeParameter($id)")
            super.visitTypeParameter(id)
        }

        override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? {
            println("visitFlexibleTypeUpperBound($flags, $typeFlexibilityId)")
            return ForType
        }

        override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor? {
            println("visitExtensions($type)")
            return object : JvmTypeExtensionVisitor() {

                override fun visit(isRaw: Boolean) {
                    println("visit($isRaw)")
                }

                override fun visitAnnotation(annotation: KmAnnotation) {
                    println("visitAnnotation($annotation)")
                    super.visitAnnotation(annotation)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

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
