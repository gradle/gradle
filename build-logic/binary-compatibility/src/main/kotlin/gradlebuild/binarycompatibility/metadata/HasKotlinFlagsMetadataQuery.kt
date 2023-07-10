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

import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmProperty
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import java.util.function.Supplier


internal
fun KotlinClassMetadata.hasKotlinFlag(memberType: MemberType, jvmSignature: String, flag: Flag): Boolean =
    hasKotlinFlags(memberType, jvmSignature) { flags ->
        flag(flags)
    }


private
fun KotlinClassMetadata.hasKotlinFlags(memberType: MemberType, jvmSignature: String, predicate: (Flags) -> Boolean): Boolean =
    when (this) {
        is KotlinClassMetadata.Class -> hasClassFlags(this::toKmClass, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.FileFacade -> hasPackageFlags(this::toKmPackage, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.MultiFileClassPart -> hasPackageFlags(this::toKmPackage, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.MultiFileClassFacade -> false
        is KotlinClassMetadata.SyntheticClass -> false
        is KotlinClassMetadata.Unknown -> false
        else -> throw IllegalStateException("Unsupported Kotlin metadata type '${this::class}'")
    }


private
typealias FlagsPredicate = (Flags) -> Boolean


private
fun hasClassFlags(kmClassSupplier: Supplier<KmClass>, memberType: MemberType, jvmSignature: String, predicate: FlagsPredicate): Boolean {
    val kmClass = kmClassSupplier.get()
    return when (memberType) {
        MemberType.TYPE -> hasTypeFlags(kmClass, jvmSignature, predicate)
        MemberType.FIELD -> hasPropertyFlags(kmClass::properties, jvmSignature, predicate)
        MemberType.CONSTRUCTOR -> hasConstructorFlags(kmClass::constructors, jvmSignature, predicate)
        MemberType.METHOD -> hasFunctionFlags(kmClass::functions, jvmSignature, predicate) ||
            hasPropertyFlags(kmClass::properties, jvmSignature, predicate)
    }
}


private
fun hasPackageFlags(kmPackageSupplier: Supplier<KmPackage>, memberType: MemberType, jvmSignature: String, predicate: FlagsPredicate): Boolean {
    val kmPackage = kmPackageSupplier.get()
    return when (memberType) {
        MemberType.FIELD -> hasPropertyFlags(kmPackage::properties, jvmSignature, predicate)
        MemberType.METHOD -> hasFunctionFlags(kmPackage::functions, jvmSignature, predicate) ||
            hasPropertyFlags(kmPackage::properties, jvmSignature, predicate)
        else -> false
    }
}


private
fun hasTypeFlags(kmClass: KmClass, jvmSignature: String, predicate: FlagsPredicate): Boolean =
    when (jvmSignature) {
        kmClass.name.replace("/", ".") -> predicate(kmClass.flags)
        else -> false
    }


private
fun hasConstructorFlags(constructorsSupplier: Supplier<MutableList<KmConstructor>>, jvmSignature: String, predicate: FlagsPredicate) =
    constructorsSupplier.get().firstOrNull { c -> jvmSignature == c.signature?.asString() }?.flags?.let { predicate(it) } ?: false


private
fun hasFunctionFlags(functionsSupplier: Supplier<MutableList<KmFunction>>, jvmSignature: String, predicate: FlagsPredicate) =
    functionsSupplier.get().firstOrNull {
        jvmSignature == it.signature?.asString()
    }?.flags?.let { predicate(it) } ?: false


private
fun hasPropertyFlags(propertiesSupplier: Supplier<MutableList<KmProperty>>, jvmSignature: String, predicate: FlagsPredicate): Boolean {
    val properties = propertiesSupplier.get()
    for (p in properties) {
        when (jvmSignature) {
            p.fieldSignature?.asString() -> return predicate(p.flags)
            p.getterSignature?.asString() -> return predicate(p.getterFlags)
            p.setterSignature?.asString() -> return predicate(p.setterFlags)
        }
    }
    return false
}
