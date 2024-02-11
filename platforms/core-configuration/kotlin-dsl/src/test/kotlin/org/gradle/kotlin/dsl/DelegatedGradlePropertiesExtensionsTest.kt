package org.gradle.kotlin.dsl

import org.gradle.api.Project
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.initialization.Settings

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.metaobject.DynamicInvokeResult

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test


class DelegatedGradlePropertiesExtensionsTest {

    @Test
    fun `non-nullable delegated property access of existing non-null gradle property`() {

        withMockForSettings(existing = "p" to 42) {

            val p: Int by settings
            assertThat(p, equalTo(42))
        }

        withMockForProject(existing = "p" to 42) {

            val p: Int by project
            assertThat(p, equalTo(42))
        }
    }

    @Test
    fun `non-nullable delegated property access of existing null gradle property throws`() {

        withMockForSettings(existing = "p" to null) {

            val p: Any by settings
            try {
                p.toString()
                fail("InvalidUserCodeException not thrown")
            } catch (ex: InvalidUserCodeException) {
                assertThat(ex.message, equalTo("Cannot get non-null property 'p' on settings as it is null"))
            }
        }

        withMockForProject(existing = "p" to null) {

            val p: Any by project
            try {
                p.toString()
                fail("InvalidUserCodeException not thrown")
            } catch (ex: InvalidUserCodeException) {
                assertThat(ex.message, equalTo("Cannot get non-null property 'p' on project as it is null"))
            }
        }
    }

    @Test
    fun `non-nullable delegated property access of non-existing gradle property throws`() {

        withMockForSettings(absent = "p") {

            val p: Any by settings
            try {
                p.toString()
                fail("InvalidUserCodeException not thrown")
            } catch (ex: InvalidUserCodeException) {
                assertThat(ex.message, equalTo("Cannot get non-null property 'p' on settings as it does not exist"))
            }
        }

        withMockForProject(absent = "p") {

            val p: Any by project
            try {
                p.toString()
                fail("InvalidUserCodeException not thrown")
            } catch (ex: InvalidUserCodeException) {
                assertThat(ex.message, equalTo("Cannot get non-null property 'p' on project as it does not exist"))
            }
        }
    }

    @Test
    fun `nullable delegated property access of existing non-null gradle property`() {

        withMockForSettings(existing = "p" to 42) {

            val p: Int? by settings
            assertThat(p, equalTo(42))
        }

        withMockForProject(existing = "p" to 42) {

            val p: Int? by project
            assertThat(p, equalTo(42))
        }
    }

    @Test
    fun `nullable delegated property access of existing null gradle property`() {

        withMockForSettings(existing = "p" to null) {

            val p: Int? by settings
            assertThat(p, nullValue())
        }

        withMockForProject(existing = "p" to null) {

            val p: Int? by project
            assertThat(p, nullValue())
        }
    }

    @Test
    fun `nullable delegated property access of non-existing gradle property`() {

        withMockForSettings(absent = "p") {

            val p: Int? by settings
            assertThat(p, nullValue())
        }

        withMockForProject(absent = "p") {

            val p: Int? by project
            assertThat(p, nullValue())
        }
    }

    private
    fun withMockForSettings(existing: Pair<String, Any?>? = null, absent: String? = null, action: DynamicDelegatedPropertiesMock.SettingsMock.() -> Unit) {
        mockForSettings(existing, absent).run {
            action()
            verifyTryGetProperty(existing, absent)
        }
    }

    private
    fun withMockForProject(existing: Pair<String, Any?>? = null, absent: String? = null, action: DynamicDelegatedPropertiesMock.ProjectMock.() -> Unit) {
        mockForProject(existing, absent).run {
            action()
            verifyTryGetProperty(existing, absent)
        }
    }

    private
    fun mockForSettings(existing: Pair<String, Any?>? = null, absent: String? = null): DynamicDelegatedPropertiesMock.SettingsMock =
        dynamicObjectMockFor(existing, absent).let { dynamicObject ->
            DynamicDelegatedPropertiesMock.SettingsMock(
                mock<DynamicAwareSettingsMockType>(name = "settings") {
                    on { asDynamicObject } doReturn dynamicObject
                },
                dynamicObject
            )
        }

    private
    fun mockForProject(existing: Pair<String, Any?>? = null, absent: String? = null): DynamicDelegatedPropertiesMock.ProjectMock =
        dynamicObjectMockFor(existing, absent).let { dynamicObject ->
            DynamicDelegatedPropertiesMock.ProjectMock(
                mock<DynamicAwareProjectMockType>(name = "project") {
                    on { asDynamicObject } doReturn dynamicObject
                },
                dynamicObject
            )
        }

    private
    interface DynamicAwareSettingsMockType : Settings, DynamicObjectAware

    private
    interface DynamicAwareProjectMockType : Project, DynamicObjectAware

    private
    sealed class DynamicDelegatedPropertiesMock<out T : Any>(private val target: T, private val dynamicObject: DynamicObject) {

        fun verifyTryGetProperty(existing: Pair<String, Any?>?, absent: String?) {
            existing?.let {
                verifyTryGetProperty(existing.first)
            }
            absent?.let {
                verifyTryGetProperty(absent)
            }
        }

        private
        fun verifyTryGetProperty(propertyName: String) =
            inOrder(target, dynamicObject) {
                verify(target as DynamicObjectAware).asDynamicObject
                verify(dynamicObject).tryGetProperty(propertyName)
                verifyNoMoreInteractions()
            }

        class SettingsMock(val settings: Settings, dynamicObject: DynamicObject) : DynamicDelegatedPropertiesMock<Settings>(settings, dynamicObject)
        class ProjectMock(val project: Project, dynamicObject: DynamicObject) : DynamicDelegatedPropertiesMock<Project>(project, dynamicObject)
    }

    private
    fun dynamicObjectMockFor(existing: Pair<String, Any?>?, absent: String?) =
        mock<DynamicObject> {
            existing?.let { (name, value) ->
                val existingMock = mock<DynamicInvokeResult> {
                    on { this.isFound } doReturn true
                    on { this.value } doReturn value
                }
                on { tryGetProperty(name) } doReturn existingMock
            }
            absent?.let {
                val absentMock = mock<DynamicInvokeResult> {
                    on { this.isFound } doReturn false
                }
                on { tryGetProperty(absent) } doReturn absentMock
            }
        }
}
