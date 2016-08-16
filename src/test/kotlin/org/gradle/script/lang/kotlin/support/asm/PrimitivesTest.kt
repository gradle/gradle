package org.gradle.script.lang.kotlin.support.asm

import org.gradle.script.lang.kotlin.codegen.classNodeFor
import org.gradle.script.lang.kotlin.codegen.forEachZipEntryIn
import org.gradle.script.lang.kotlin.support.asm.fixture.GenericMethodEraserFixture
import org.gradle.script.lang.kotlin.support.asm.fixture.MethodEraserFixture
import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.zipOf

import org.objectweb.asm.tree.MethodNode

import java.io.ByteArrayOutputStream
import java.io.InputStream

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class PrimitivesTest {

    @Test
    fun `#eraseMethodsMatching will erase methods accepted by given predicate`() {

        // Remove all methods with an Int parameter type
        val output = eraseMethodsFrom(MethodEraserFixture::class.java) {
            it.signature.parameters.any {
                it.kotlinTypeName == "Int"
            }
        }

        assertThat(
            methodsIn(output),
            equalTo(
                listOf(
                    "MethodEraserFixture.m3(Ljava/lang/String;)V",
                    "MethodEraserFixture.<init>()V")))
    }

    @Test
    fun `#eraseMethodsMatching will NOT remove methods from generic types`() {

        // Try to erase all methods
        val output = eraseMethodsFrom(GenericMethodEraserFixture::class.java) {
            true
        }
        assertThat(
            methodsIn(output),
            equalTo(
                listOf(
                    "GenericMethodEraserFixture.m4(ILjava/lang/Object;)V",
                    "GenericMethodEraserFixture.<init>()V")))
    }

    private fun eraseMethodsFrom(clazz: Class<*>, predicate: MethodPredicate): ByteArray =
        eraseMethodsFrom(zipOf(classEntriesFor(clazz)), predicate)

    fun eraseMethodsFrom(jar: ByteArray, predicate: MethodPredicate): ByteArray =
        ByteArrayOutputStream().run {
            eraseMethodsMatching(predicate, jar.inputStream(), this)
            toByteArray()
        }

    fun methodsIn(output: ByteArray) =
        methodsIn(output.inputStream())

    fun methodsIn(input: InputStream): List<String> {
        val methods = arrayListOf<String>()
        forEachZipEntryIn(input) {
            if (zipEntry.name.endsWith(".class")) {
                val classNode = classNodeFor(zipInputStream)
                val className = classNode.name.substringAfterLast('/')
                methods.addAll(
                    classNode
                        .methods
                        .filterIsInstance<MethodNode>()
                        .map { "$className.${it.name}${it.desc}" })
            }
        }
        return methods
    }
}
