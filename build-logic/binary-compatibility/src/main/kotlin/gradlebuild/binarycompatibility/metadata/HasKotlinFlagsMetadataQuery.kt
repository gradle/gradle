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

package gradlebuild.binarycompatibility.metadata

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmClassExtensionVisitor
import kotlinx.metadata.KmClassVisitor
import kotlinx.metadata.KmConstructorExtensionVisitor
import kotlinx.metadata.KmConstructorVisitor
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmFunctionExtensionVisitor
import kotlinx.metadata.KmFunctionVisitor
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.jvm.JvmClassExtensionVisitor
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KotlinClassMetadata


internal
fun KotlinClassMetadata.hasKotlinFlag(memberType: MemberType, jvmSignature: String, flag: Flag): Boolean =
    hasKotlinFlags(memberType, jvmSignature) { flags ->
        flag(flags)
    }


private
fun KotlinClassMetadata.hasKotlinFlags(memberType: MemberType, jvmSignature: String, predicate: (Flags) -> Boolean): Boolean =
    when (this) {
        is KotlinClassMetadata.Class -> classVisitorFor(memberType, jvmSignature, predicate).apply(::accept).isSatisfied
        is KotlinClassMetadata.FileFacade -> packageVisitorFor(memberType, jvmSignature, predicate).apply(::accept).isSatisfied
        is KotlinClassMetadata.MultiFileClassPart -> packageVisitorFor(memberType, jvmSignature, predicate).apply(::accept).isSatisfied
        is KotlinClassMetadata.MultiFileClassFacade -> false
        is KotlinClassMetadata.SyntheticClass -> false
        is KotlinClassMetadata.Unknown -> false
        else -> throw IllegalStateException("Unsupported Kotlin metadata type '${this::class}'")
    }


private
typealias FlagsPredicate = (Flags) -> Boolean


private
interface CanBeSatisfied {
    var isSatisfied: Boolean
}


private
abstract class SatisfiableClassVisitor : KmClassVisitor(), CanBeSatisfied


private
abstract class SatisfiablePackageVisitor : KmPackageVisitor(), CanBeSatisfied


@Suppress("unchecked_cast")
private
fun classVisitorFor(memberType: MemberType, jvmSignature: String, predicate: FlagsPredicate): SatisfiableClassVisitor =
    when (memberType) {
        MemberType.TYPE -> TypeFlagsKmClassVisitor(jvmSignature, predicate)
        MemberType.FIELD -> FieldFlagsKmClassVisitor(jvmSignature, predicate)
        MemberType.CONSTRUCTOR -> ConstructorFlagsKmClassVisitor(jvmSignature, predicate)
        MemberType.METHOD -> MethodFlagsKmClassVisitor(jvmSignature, predicate)
    }


@Suppress("unchecked_cast")
private
fun packageVisitorFor(memberType: MemberType, jvmSignature: String, predicate: FlagsPredicate): SatisfiablePackageVisitor =
    when (memberType) {
        MemberType.FIELD -> FieldFlagsKmPackageVisitor(jvmSignature, predicate)
        MemberType.METHOD -> MethodFlagsKmPackageVisitor(jvmSignature, predicate)
        else -> NoopFlagsKmPackageVisitor
    }


private
class TypeFlagsKmClassVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiableClassVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    override fun visit(flags: Flags, name: ClassName) {
        if (!isDoneVisiting && jvmSignature == name.replace("/", ".")) {
            isSatisfied = predicate(flags)
            isDoneVisiting = true
        }
    }
}


private
class FieldFlagsKmClassVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiableClassVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { satisfaction: Boolean ->
        isSatisfied = satisfaction
        isDoneVisiting = true
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmClassExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class ConstructorFlagsKmClassVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiableClassVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
        if (isDoneVisiting) null
        else object : KmConstructorVisitor() {
            override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor =
                object : JvmConstructorExtensionVisitor() {
                    override fun visit(signature: JvmMethodSignature?) {
                        if (jvmSignature == signature?.asString()) {
                            isSatisfied = predicate(flags)
                            isDoneVisiting = true
                        }
                    }
                }
        }
}


private
class MethodFlagsKmClassVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiableClassVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { satisfaction: Boolean ->
        isSatisfied = satisfaction
        isDoneVisiting = true
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
        if (isDoneVisiting) null
        else MethodFlagsKmFunctionVisitor(jvmSignature, predicate, flags, onMatch)

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmClassExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class FieldFlagsKmPackageVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiablePackageVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { satisfaction: Boolean ->
        isSatisfied = satisfaction
        isDoneVisiting = true
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmPackageExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class MethodFlagsKmPackageVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate
) : SatisfiablePackageVisitor() {

    override var isSatisfied = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { satisfaction: Boolean ->
        isSatisfied = satisfaction
        isDoneVisiting = true
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
        if (isDoneVisiting) null
        else MethodFlagsKmFunctionVisitor(jvmSignature, predicate, flags, onMatch)

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmPackageExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                MemberFlagsKmPropertyExtensionVisitor(jvmSignature, predicate, flags, getterFlags, setterFlags, onMatch)
        }
}


private
object NoopFlagsKmPackageVisitor : SatisfiablePackageVisitor() {
    override var isSatisfied = false
}


private
class MemberFlagsKmPropertyExtensionVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate,
    private val fieldFlags: Flags,
    private val getterFlags: Flags,
    private val setterFlags: Flags,
    private val onMatch: (Boolean) -> Unit
) : KmPropertyVisitor() {

    private
    val kmPropertyExtensionVisitor = object : JvmPropertyExtensionVisitor() {
        override fun visit(fieldSignature: JvmFieldSignature?, getterSignature: JvmMethodSignature?, setterSignature: JvmMethodSignature?) {
            when (jvmSignature) {
                fieldSignature?.asString() -> onMatch(predicate(fieldFlags))
                getterSignature?.asString() -> onMatch(predicate(getterFlags))
                setterSignature?.asString() -> onMatch(predicate(setterFlags))
            }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor =
        kmPropertyExtensionVisitor
}


private
class MethodFlagsKmFunctionVisitor(
    private val jvmSignature: String,
    private val predicate: FlagsPredicate,
    private val functionFlags: Flags,
    private val onMatch: (Boolean) -> Unit
) : KmFunctionVisitor() {

    private
    val kmFunctionExtensionVisitor = object : JvmFunctionExtensionVisitor() {
        override fun visit(signature: JvmMethodSignature?) {
            if (jvmSignature == signature?.asString()) {
                onMatch(predicate(functionFlags))
            }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? =
        kmFunctionExtensionVisitor
}
