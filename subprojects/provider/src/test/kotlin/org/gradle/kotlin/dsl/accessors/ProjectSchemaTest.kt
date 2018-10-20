package org.gradle.kotlin.dsl.accessors

import org.gradle.api.Project
import org.gradle.api.reflect.TypeOf

import org.gradle.internal.classpath.ClassPath

import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.support.useToRun

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Test


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
            val type = SchemaType(TypeOf.typeOf(loadClass(typeString)))
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

//        val typeString = "non.visible.Type"
//
//        val classPath = classPathWithPrivateType(typeString)
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("buildScan" to typeString),
//            classPath
//        )
//
//        assertThat(
//            projectSchema.extension("buildScan").type,
//            equalTo(inaccessible(typeString, nonPublic(typeString)))
//        )
    }

    @Test
    fun `public synthetic type is represented as Inaccessible because Synthetic`() {

//        val typeString = "synthetic.Type"
//
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("buildScan" to typeString),
//            classPathWithType(typeString, ACC_PUBLIC, ACC_SYNTHETIC))
//
//        assertThat(
//            projectSchema.extension("buildScan").type,
//            equalTo(inaccessible(typeString, synthetic(typeString))))
    }

    @Test
    fun `parameterized public type with public component type is represented as Accessible`() {

//        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())
//
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("generic" to genericTypeString),
//            classPathWith(PublicGenericType::class, PublicComponentType::class))
//
//        assertThat(
//            projectSchema.extension("generic").type,
//            equalTo(accessible(genericTypeString)))
    }

    @Test
    fun `parameterized public type with non public component type is represented as Inaccessible because of NonPublic component type`() {

//        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PrivateComponentType>>())
//
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("generic" to genericTypeString),
//            classPathWith(PublicGenericType::class, PrivateComponentType::class))
//
//        assertThat(
//            projectSchema.extension("generic").type,
//            equalTo(inaccessible(genericTypeString, nonPublic(PrivateComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `parameterized public type with non existing component type is represented as Inaccessible because of NonAvailable component type`() {

//        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())
//
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("generic" to genericTypeString),
//            classPathWith(PublicGenericType::class))
//
//        assertThat(
//            projectSchema.extension("generic").type,
//            equalTo(inaccessible(genericTypeString, nonAvailable(PublicComponentType::class.qualifiedName!!))))
    }

    @Test
    fun `public type from jar is represented as Accessible`() {
//
//        val genericTypeString = kotlinTypeStringFor(typeOf<PublicGenericType<PublicComponentType>>())
//
//        val projectSchema = availableProjectSchemaFor(
//            schemaWithExtensions("generic" to genericTypeString),
//            jarClassPathWith(PublicGenericType::class, PublicComponentType::class))
//
//        assertThat(
//            projectSchema.extension("generic").type,
//            equalTo(accessible(genericTypeString)))
    }

    @Test
    fun `#groupedByTarget`() {

        val schema =
            ProjectSchema(
                extensions = listOf(
                    ProjectSchemaEntry("Project", "ext", "Ext"),
                    ProjectSchemaEntry("Project", "java", "Java"),
                    ProjectSchemaEntry("Task", "ext", "Ext")
                ),
                conventions = listOf(
                    ProjectSchemaEntry("Project", "base", "Base"),
                    ProjectSchemaEntry("Task", "meta", "Meta")
                ),
                tasks = emptyList(),
                containerElements = emptyList(),
                configurations = listOf(
                    "api",
                    "implementation"
                )
            )

        val groupedSchema =
            schema.groupedByTarget()

        assertThat(
            groupedSchema,
            equalTo(
                mapOf(
                    "Project" to ProjectSchema(
                        extensions = listOf(
                            ProjectSchemaEntry("Project", "ext", "Ext"),
                            ProjectSchemaEntry("Project", "java", "Java")
                        ),
                        conventions = listOf(
                            ProjectSchemaEntry("Project", "base", "Base")
                        ),
                        tasks = emptyList(),
                        containerElements = emptyList(),
                        configurations = emptyList()
                    ),

                    "Task" to ProjectSchema(
                        extensions = listOf(
                            ProjectSchemaEntry("Task", "ext", "Ext")
                        ),
                        conventions = listOf(
                            ProjectSchemaEntry("Task", "meta", "Meta")
                        ),
                        tasks = emptyList(),
                        containerElements = emptyList(),
                        configurations = emptyList()
                    )
                )
            )
        )
    }

    private
    fun schemaWithExtensions(vararg pairs: Pair<String, SchemaType>) =
        ProjectSchema(
            extensions = pairs.map { ProjectSchemaEntry(SchemaType.of<Project>(), it.first, it.second) },
            conventions = emptyList(),
            tasks = emptyList(),
            containerElements = emptyList(),
            configurations = emptyList())

    private
    fun <T> ProjectSchema<T>.extension(name: String) =
        extensions.single { it.name == name }
}
