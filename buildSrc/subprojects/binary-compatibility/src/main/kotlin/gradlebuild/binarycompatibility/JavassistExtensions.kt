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

@file:JvmName("JavassistExtensions")

package gradlebuild.binarycompatibility

import javassist.CtClass
import javassist.CtMethod
import javassist.bytecode.SyntheticAttribute
import javassist.bytecode.annotation.AnnotationMemberValue
import javassist.bytecode.annotation.ArrayMemberValue
import javassist.bytecode.annotation.BooleanMemberValue
import javassist.bytecode.annotation.ByteMemberValue
import javassist.bytecode.annotation.CharMemberValue
import javassist.bytecode.annotation.ClassMemberValue
import javassist.bytecode.annotation.DoubleMemberValue
import javassist.bytecode.annotation.EnumMemberValue
import javassist.bytecode.annotation.FloatMemberValue
import javassist.bytecode.annotation.IntegerMemberValue
import javassist.bytecode.annotation.LongMemberValue
import javassist.bytecode.annotation.MemberValue
import javassist.bytecode.annotation.MemberValueVisitor
import javassist.bytecode.annotation.ShortMemberValue
import javassist.bytecode.annotation.StringMemberValue
import org.gradle.api.reflect.TypeOf
import org.gradle.kotlin.dsl.*


internal
val CtClass.isKotlin: Boolean
    get() = hasAnnotation(Metadata::class.qualifiedName)


internal
val CtMethod.isSynthetic
    get() = methodInfo.getAttribute(SyntheticAttribute.tag) != null


internal
val MemberValue.intValue: Int
    get() {
        var value: Int? = null
        accept(object : MemberValueVisitorAdapter() {
            override fun visitIntegerMemberValue(node: IntegerMemberValue) {
                value = node.value
            }
        })
        if (value == null) throw annotationMemberValueNotFound(typeOf<Int>())
        return value!!
    }


internal
val MemberValue.stringValue: String
    get() {
        var value: String? = null
        accept(object : MemberValueVisitorAdapter() {
            override fun visitStringMemberValue(node: StringMemberValue) {
                value = node.value
            }
        })
        if (value == null) throw annotationMemberValueNotFound(typeOf<String>())
        return value!!
    }


internal
val MemberValue.intArrayValue: IntArray
    get() {
        var value: IntArray? = null
        accept(object : MemberValueVisitorAdapter() {
            override fun visitArrayMemberValue(node: ArrayMemberValue) {
                value = node.value.map { it.intValue }.toIntArray()
            }
        })
        if (value == null) throw annotationMemberValueNotFound(typeOf<IntArray>())
        return value!!
    }


internal
val MemberValue.stringArrayValue: Array<String>
    get() {
        var value: Array<String>? = null
        accept(object : MemberValueVisitorAdapter() {
            override fun visitArrayMemberValue(node: ArrayMemberValue) {
                value = node.value.map { it.stringValue }.toTypedArray()
            }
        })
        if (value == null) throw annotationMemberValueNotFound(typeOf<Array<String>>())
        return value!!
    }


private
fun <T> annotationMemberValueNotFound(type: TypeOf<T>) =
    IllegalStateException("Annotation member value '${type.simpleName}' not found")


private
open class MemberValueVisitorAdapter : MemberValueVisitor {
    override fun visitStringMemberValue(node: StringMemberValue) = Unit
    override fun visitBooleanMemberValue(node: BooleanMemberValue) = Unit
    override fun visitLongMemberValue(node: LongMemberValue) = Unit
    override fun visitArrayMemberValue(node: ArrayMemberValue) = Unit
    override fun visitShortMemberValue(node: ShortMemberValue) = Unit
    override fun visitClassMemberValue(node: ClassMemberValue) = Unit
    override fun visitAnnotationMemberValue(node: AnnotationMemberValue) = Unit
    override fun visitIntegerMemberValue(node: IntegerMemberValue) = Unit
    override fun visitEnumMemberValue(node: EnumMemberValue) = Unit
    override fun visitByteMemberValue(node: ByteMemberValue) = Unit
    override fun visitDoubleMemberValue(node: DoubleMemberValue) = Unit
    override fun visitFloatMemberValue(node: FloatMemberValue) = Unit
    override fun visitCharMemberValue(node: CharMemberValue) = Unit
}
