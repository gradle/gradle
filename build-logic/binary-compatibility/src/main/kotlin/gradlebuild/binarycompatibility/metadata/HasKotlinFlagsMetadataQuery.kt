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

import kotlinx.metadata.KmClass
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmPackage
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmPropertyAccessorAttributes
import kotlinx.metadata.Visibility
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature
import kotlinx.metadata.visibility
import java.util.function.Supplier


internal
fun KotlinClassMetadata.hasAttribute(memberType: MemberType, jvmSignature: String, predicate: AttributePredicate): Boolean =
    when (this) {
        is KotlinClassMetadata.Class -> hasClassAttribute(this::kmClass, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.FileFacade -> hasPackageAttribute(this::kmPackage, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.MultiFileClassPart -> hasPackageAttribute(this::kmPackage, memberType, jvmSignature, predicate)
        is KotlinClassMetadata.MultiFileClassFacade -> false
        is KotlinClassMetadata.SyntheticClass -> false
        is KotlinClassMetadata.Unknown -> false
        else -> error("Unsupported Kotlin metadata type '${this::class}'")
    }


private
fun hasClassAttribute(kmClassSupplier: Supplier<KmClass>, memberType: MemberType, jvmSignature: String, predicate: AttributePredicate): Boolean {
    val kmClass = kmClassSupplier.get()
    return when (memberType) {
        MemberType.TYPE -> hasTypeAttribute(kmClass, jvmSignature, predicate)
        MemberType.FIELD -> hasPropertyAttribute(kmClass::properties, jvmSignature, predicate)
        MemberType.CONSTRUCTOR -> hasConstructorAttribute(kmClass::constructors, jvmSignature, predicate)
        MemberType.METHOD -> hasFunctionAttribute(kmClass::functions, jvmSignature, predicate) ||
            hasPropertyAttribute(kmClass::properties, jvmSignature, predicate)
    }
}


private
fun hasPackageAttribute(kmPackageSupplier: Supplier<KmPackage>, memberType: MemberType, jvmSignature: String, predicate: AttributePredicate): Boolean {
    val kmPackage = kmPackageSupplier.get()
    return when (memberType) {
        MemberType.FIELD -> hasPropertyAttribute(kmPackage::properties, jvmSignature, predicate)
        MemberType.METHOD -> hasFunctionAttribute(kmPackage::functions, jvmSignature, predicate) ||
            hasPropertyAttribute(kmPackage::properties, jvmSignature, predicate)
        else -> false
    }
}


private
fun hasTypeAttribute(kmClass: KmClass, jvmSignature: String, predicate: AttributePredicate): Boolean =
    when (jvmSignature) {
        kmClass.name.replace("/", ".") -> predicate.match(kmClass)
        else -> false
    }


private
fun hasConstructorAttribute(constructorsSupplier: Supplier<MutableList<KmConstructor>>, jvmSignature: String, predicate: AttributePredicate) =
    constructorsSupplier.get().firstOrNull { c -> jvmSignature == c.signature?.toString() }?.let { predicate.match(it) } ?: false


private
fun hasFunctionAttribute(functionsSupplier: Supplier<MutableList<KmFunction>>, jvmSignature: String, predicate: AttributePredicate) =
    functionsSupplier.get().firstOrNull {
        jvmSignature == it.signature?.toString()
    }?.let { predicate.match(it) } ?: false


private
fun hasPropertyAttribute(propertiesSupplier: Supplier<MutableList<KmProperty>>, jvmSignature: String, predicate: AttributePredicate): Boolean {
    val properties = propertiesSupplier.get()
    for (p in properties) {
        when (jvmSignature) {
            p.fieldSignature?.toString() -> return predicate.match(p)
            p.getterSignature?.toString() -> return predicate.match(p.getter)
            p.setterSignature?.toString() -> return p.setter?.let { predicate.match(it) } ?: false
        }
    }
    return false
}

interface AttributePredicate {
    fun match(kmClass: KmClass): Boolean
    fun match(kmConstructor: KmConstructor): Boolean
    fun match(kmProperty: KmProperty): Boolean
    fun match(kmFunction: KmFunction): Boolean
    fun match(kmPropertyAccessorAttributes: KmPropertyAccessorAttributes): Boolean

    companion object Factory {
        fun visibility(visibility: Visibility): AttributePredicate = object: AttributePredicate {
            override fun match(kmClass: KmClass) = kmClass.visibility == visibility
            override fun match(kmConstructor: KmConstructor) = kmConstructor.visibility == visibility
            override fun match(kmProperty: KmProperty) = kmProperty.visibility == visibility
            override fun match(kmFunction: KmFunction) = kmFunction.visibility == visibility
            override fun match(kmPropertyAccessorAttributes: KmPropertyAccessorAttributes) = kmPropertyAccessorAttributes.visibility == visibility
        }

        fun functionAttribute(test: (KmFunction) -> Boolean): AttributePredicate = object: AttributePredicate {
            override fun match(kmClass: KmClass) = false
            override fun match(kmConstructor: KmConstructor) = false
            override fun match(kmProperty: KmProperty) = false
            override fun match(kmFunction: KmFunction) = test(kmFunction)
            override fun match(kmPropertyAccessorAttributes: KmPropertyAccessorAttributes) = false
        }
    }
}
