package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Project

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.provider.spi.ProjectSchema
import org.gradle.kotlin.dsl.provider.spi.ProjectSchemaEntry
import org.gradle.kotlin.dsl.provider.spi.kotlinTypeStringFor

import org.gradle.kotlin.dsl.typeOf

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Test

import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC


@Suppress("unused")
class PublicGenericType<T>


class PublicComponentType


private
class PrivateComponentType


class ProjectSchemaTest : TestWithClassPath() {

    @Test
    fun `#isLegalAccessorName rejects illegal Kotlin extension names`() {

        assert(isLegalAccessorName("foo_bar"))
        assert(isLegalAccessorName("foo-bar"))
        assert(isLegalAccessorName("foo bar"))
        assert(isLegalAccessorName("'foo'bar'"))
        assert(isLegalAccessorName("foo${'$'}${'$'}bar"))

        assertFalse(isLegalAccessorName("foo`bar"))
        assertFalse(isLegalAccessorName("foo.bar"))
        assertFalse(isLegalAccessorName("foo/bar"))
        assertFalse(isLegalAccessorName("foo\\bar"))
    }

    @Test
    fun `accessor name spec escapes string template dollar signs`() {
        val original = "foo${'$'}${'$'}bar"
        val spec = AccessorNameSpec(original)

        assertThat(spec.original, equalTo(original))
        assertThat(spec.kotlinIdentifier, equalTo(original))
        assertThat(spec.stringLiteral, equalTo("foo${'$'}{'${'$'}'}${'$'}{'${'$'}'}bar"))
    }

    @Test
    fun `non existing type is represented as Inaccessible because NonAvailable`() {

        val typeString = "non.existing.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            ClassPath.EMPTY)

        assertThat(
            projectSchema.extension("buildScan").type,
            equalTo(inaccessible(typeString, nonAvailable(typeString))))
    }

    @Test
    fun `public type is represented as Accessible`() {

        val typeString = "existing.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithPublicType(typeString))

        assertThat(
            projectSchema.extension("buildScan").type,
            equalTo(accessible(typeString)))
    }

    @Test
    fun `private type is represented as Inaccessible because NonPublic`() {

        val typeString = "non.visible.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithPrivateType(typeString))

        assertThat(
            projectSchema.extension("buildScan").type,
            equalTo(inaccessible(typeString, nonPublic(typeString))))
    }

    @Test
    fun `public synthetic type is represented as Inaccessible because Synthetic`() {

        val typeString = "synthetic.Type"

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to typeString),
            classPathWithType(typeString, ACC_PUBLIC, ACC_SYNTHETIC))

        assertThat(
            projectSchema.extension("buildScan").type,
            equalTo(inaccessible(typeString, synthetic(typeString))))
    }

    @Test
    fun `parameterized public type with public component type is represented as Accessible`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class, PublicComponentType::class))

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(accessible(genericTypeString)))
    }

    @Test
    fun `parameterized public type with non public component type is represented as Inaccessible because of NonPublic component type`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PrivateComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class, PrivateComponentType::class))

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(inaccessible(genericTypeString, nonPublic(PrivateComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `parameterized public type with non existing component type is represented as Inaccessible because of NonAvailable component type`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            classPathWith(PublicGenericType::class))

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(inaccessible(genericTypeString, nonAvailable(PublicComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `public type from jar is represented as Accessible`() {

        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericTypeString),
            jarClassPathWith(PublicGenericType::class, PublicComponentType::class))

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(accessible(genericTypeString)))
    }

    private
    fun schemaWithExtensions(vararg pairs: Pair<String, String>) =
        ProjectSchema(
            extensions = pairs.map { ProjectSchemaEntry(Project::class.java.name!!, it.first, it.second) },
            conventions = emptyList(),
            configurations = emptyList())

    private
    fun <T> ProjectSchema<T>.extension(name: String) =
        extensions.single { it.name == name }
}
