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

import kotlinx.metadata.KmPackage
import kotlinx.metadata.jvm.JvmMethodSignature

import org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter


internal
data class AccessorFragment(
    val source: String,
    val bytecode: BytecodeWriter,
    val metadata: MetadataWriter,
    val signature: JvmMethodSignature
)


internal
typealias BytecodeWriter = BytecodeFragmentScope.() -> Unit


internal
class BytecodeFragmentScope(
    val signature: JvmMethodSignature,
    writer: ClassWriter
) : ClassVisitor(ASM_LEVEL, writer)


internal
typealias MetadataWriter = MetadataFragmentScope.() -> Unit


internal
data class MetadataFragmentScope(
    val signature: JvmMethodSignature,
    val kmPackage: KmPackage
)


internal
typealias Fragments = Pair<String, Sequence<AccessorFragment>>
