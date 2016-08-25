package codegen

import org.gradle.script.lang.kotlin.codegen.ClassType
import org.gradle.script.lang.kotlin.codegen.GenericType
import org.gradle.script.lang.kotlin.codegen.GenericTypeVariable
import org.gradle.script.lang.kotlin.codegen.MethodSignature
import org.gradle.script.lang.kotlin.codegen.PrimitiveType
import org.gradle.script.lang.kotlin.codegen.TypeParameter

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class MethodSignatureTest {

    @Test
    fun `can parse generic parameter type`() {
        assertThat(
            MethodSignature.from("(Lorg/gradle/api/Action<-Lorg/gradle/api/Project;>;)V"),
            equalTo(
                MethodSignature(
                    parameters = listOf(
                        GenericType(
                            ClassType("org/gradle/api/Action"),
                            listOf(ClassType("org/gradle/api/Project"))
                        )),
                    returnType = PrimitiveType('V'))))
    }

    @Test
    fun `can parse generic method`() {
        assertThat(
            MethodSignature.from(
                "<T:Ljava/lang/Object;>(Ljava/lang/Iterable<TT;>;Lorg/gradle/api/Action<-TT;>;)Ljava/lang/Iterable<TT;>;"),
            equalTo(
                MethodSignature(
                    typeParameters = listOf(TypeParameter("T", ClassType("java/lang/Object"))),
                    parameters = listOf(
                        GenericType(ClassType("java/lang/Iterable"), listOf(GenericTypeVariable("T"))),
                        GenericType(ClassType("org/gradle/api/Action"), listOf(GenericTypeVariable("T")))),
                    returnType = GenericType(ClassType("java/lang/Iterable"), listOf(GenericTypeVariable("T"))))))
    }
}
