package org.gradle.script.lang.kotlin.support.asm

import org.gradle.script.lang.kotlin.codegen.classNodeFor
import org.gradle.script.lang.kotlin.codegen.forEachZipEntryIn
import org.gradle.script.lang.kotlin.support.asm.fixture.GenericRemoveMethodsFixture
import org.gradle.script.lang.kotlin.support.asm.fixture.RemoveMethodsFixture
import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.zipOf

import org.objectweb.asm.tree.MethodNode

import java.io.ByteArrayOutputStream
import java.io.InputStream

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class RemoveMethodsMatchingTest {

    @Test
    fun `#removeMethodsMatching will remove all methods accepted by the given predicate`() {

        // Remove all methods with an Int parameter type
        val output = removeMethodsFrom(RemoveMethodsFixture::class.java) {
            it.signature.parameters.any {
                it.kotlinTypeName == "Int"
            }
        }

        assertThat(
            methodsIn(output),
            equalTo(
                listOf(
                    "RemoveMethodsFixture.m3(Ljava/lang/String;)V",
                    "RemoveMethodsFixture.<init>()V")))
    }

    @Test
    fun `#removeMethodsMatching will NOT remove methods from generic types`() {

        // Try to erase all methods
        val output = removeMethodsFrom(GenericRemoveMethodsFixture::class.java) {
            true
        }
        assertThat(
            methodsIn(output),
            equalTo(
                listOf(
                    "GenericRemoveMethodsFixture.m4(ILjava/lang/Object;)V",
                    "GenericRemoveMethodsFixture.<init>()V")))
    }

    fun removeMethodsFrom(clazz: Class<*>, predicate: MethodPredicate): ByteArray =
        removeMethodsFrom(zipOf(classEntriesFor(clazz)), predicate)

    fun removeMethodsFrom(jar: ByteArray, predicate: MethodPredicate): ByteArray =
        ByteArrayOutputStream().run {
            removeMethodsMatching(predicate, jar.inputStream(), this)
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
