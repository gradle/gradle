package org.gradle.kotlin.dsl.accessors

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.support.useToRun

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
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

    interface NonExistingType

    @Test
    fun `non existing type is represented as Inaccessible because NonAvailable`() {

        val type = SchemaType.of<NonExistingType>()

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("buildScan" to type),
            ClassPath.EMPTY
        )

        assertThat(
            projectSchema.extension("buildScan").type,
            equalTo(inaccessible(type, nonAvailable(type.kotlinString)))
        )
    }

    @Test
    fun `public type is represented as Accessible`() {

        val typeString = "existing.Type"

        val classPath = classPathWithPublicType(typeString)
        classLoaderFor(classPath).useToRun {
            val type = schemaTypeFor(typeString)
            val projectSchema = availableProjectSchemaFor(
                schemaWithExtensions("buildScan" to type),
                classPath
            )

            assertThat(
                projectSchema.extension("buildScan").type,
                equalTo(accessible(type))
            )
        }
    }

    @Test
    fun `private type is represented as Inaccessible because NonPublic`() {

        val typeString = "non.visible.Type"

        val classPath = classPathWithPrivateType(typeString)
        classLoaderFor(classPath).useToRun {
            val type = schemaTypeFor(typeString)
            val projectSchema = availableProjectSchemaFor(
                schemaWithExtensions("buildScan" to type),
                classPath
            )

            assertThat(
                projectSchema.extension("buildScan").type,
                equalTo(inaccessible(type, nonPublic(typeString)))
            )
        }
    }

    @Test
    fun `public synthetic type is represented as Inaccessible because Synthetic`() {

        val typeString = "synthetic.Type"

        val classPath = classPathWithType(typeString, ACC_PUBLIC, ACC_SYNTHETIC)
        classLoaderFor(classPath).useToRun {
            val type = schemaTypeFor(typeString)
            val projectSchema = availableProjectSchemaFor(
                schemaWithExtensions("buildScan" to type),
                classPath
            )

            assertThat(
                projectSchema.extension("buildScan").type,
                equalTo(inaccessible(type, synthetic(typeString)))
            )
        }
    }

    @Test
    fun `parameterized public type with public component type is represented as Accessible`() {

        val genericType = SchemaType.of<PublicGenericType<PublicComponentType>>()

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericType),
            classPathWith(PublicGenericType::class, PublicComponentType::class)
        )

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(accessible(genericType))
        )
    }

    @Test
    fun `parameterized public type with non public component type is represented as Inaccessible because of NonPublic component type`() {

        val genericType = SchemaType.of<PublicGenericType<PrivateComponentType>>()

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericType),
            classPathWith(PublicGenericType::class, PrivateComponentType::class)
        )

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(inaccessible(genericType, nonPublic(PrivateComponentType::class.qualifiedName!!)))
        )
    }

    @Test
    fun `parameterized public type with non existing component type is represented as Inaccessible because of NonAvailable component type`() {

        val genericType = SchemaType.of<PublicGenericType<PublicComponentType>>()

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericType),
            classPathWith(PublicGenericType::class)
        )

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(inaccessible(genericType, nonAvailable(PublicComponentType::class.qualifiedName!!)))
        )
    }

    @Test
    fun `public type from jar is represented as Accessible`() {

        val genericType = SchemaType.of<PublicGenericType<PublicComponentType>>()

        val projectSchema = availableProjectSchemaFor(
            schemaWithExtensions("generic" to genericType),
            jarClassPathWith(PublicGenericType::class, PublicComponentType::class)
        )

        assertThat(
            projectSchema.extension("generic").type,
            equalTo(accessible(genericType))
        )
    }

    @Test
    fun `cache key takes configurations into account`() {

        assertThat(
            cacheKeyFor(
                configurations = listOf("api")
            ),
            not(equalTo(cacheKeyFor(
                configurations = listOf("implementation")
            )))
        )
    }

    @Test
    fun `cache key takes container element names into account`() {

        assertThat(
            cacheKeyFor(
                containerElements = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            not(equalTo(cacheKeyFor(
                containerElements = listOf(
                    entry<TaskContainer, DefaultTask>("clean")
                )
            )))
        )
    }

    @Test
    fun `cache key takes container element types into account`() {

        assertThat(
            cacheKeyFor(
                containerElements = listOf(
                    entry<TaskContainer, DefaultTask>("clean")
                )
            ),
            not(equalTo(cacheKeyFor(
                containerElements = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            )))
        )
    }

    @Test
    fun `cache key takes task names into account`() {

        assertThat(
            cacheKeyFor(
                tasks = listOf(
                    entry<TaskContainer, DefaultTask>("assemble")
                )
            ),
            not(equalTo(cacheKeyFor(
                tasks = listOf(
                    entry<TaskContainer, DefaultTask>("clean")
                )
            )))
        )
    }

    @Test
    fun `cache key takes task types into account`() {

        assertThat(
            cacheKeyFor(
                tasks = listOf(
                    entry<TaskContainer, DefaultTask>("clean")
                )
            ),
            not(equalTo(cacheKeyFor(
                tasks = listOf(
                    entry<TaskContainer, Delete>("clean")
                )
            )))
        )
    }

    private
    fun schemaWithExtensions(vararg pairs: Pair<String, SchemaType>) = projectSchemaWith(
        extensions = pairs.map { ProjectSchemaEntry(SchemaType.of<Project>(), it.first, it.second) }
    )

    private
    fun cacheKeyFor(
        extensions: TypedProjectSchemaEntryList = emptyList(),
        conventions: TypedProjectSchemaEntryList = emptyList(),
        tasks: TypedProjectSchemaEntryList = emptyList(),
        containerElements: TypedProjectSchemaEntryList = emptyList(),
        configurations: List<String> = emptyList()
    ): String = projectSchemaWith(
        extensions = extensions,
        conventions = conventions,
        tasks = tasks,
        containerElements = containerElements,
        configurations = configurations
    ).toCacheKeyString()

    private
    fun projectSchemaWith(
        extensions: TypedProjectSchemaEntryList = emptyList(),
        conventions: TypedProjectSchemaEntryList = emptyList(),
        tasks: TypedProjectSchemaEntryList = emptyList(),
        containerElements: TypedProjectSchemaEntryList = emptyList(),
        configurations: List<String> = emptyList()
    ) = TypedProjectSchema(
        extensions = extensions,
        conventions = conventions,
        tasks = tasks,
        containerElements = containerElements,
        configurations = configurations
    )

    private
    fun <T> ProjectSchema<T>.extension(name: String) =
        extensions.single { it.name == name }
}


internal
typealias TypedProjectSchemaEntryList = List<ProjectSchemaEntry<SchemaType>>


internal
fun ClassLoader.schemaTypeFor(typeString: String): SchemaType =
    SchemaType(loadTypeOf(typeString))


internal
fun ClassLoader.loadTypeOf(typeString: String) =
    TypeOf.typeOf<Any?>(loadClass(typeString))
