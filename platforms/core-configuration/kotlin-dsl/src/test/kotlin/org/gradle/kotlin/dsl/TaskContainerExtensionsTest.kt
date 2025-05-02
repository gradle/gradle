package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class TaskContainerExtensionsTest {

    @Suppress("DEPRECATION")
    @Test
    fun `can create tasks with injected constructor arguments`() {

        val task = mock<DefaultTask>()
        val tasks = mock<TaskContainer> {
            on { create("my", DefaultTask::class.java, "foo", "bar") } doReturn task
        }

        tasks.create<DefaultTask>("my", "foo", "bar")

        verify(tasks).create("my", DefaultTask::class.java, "foo", "bar")
    }

    @Test
    fun `can register tasks with injected constructor arguments`() {

        val taskProvider = mock<TaskProvider<Task>>()
        val tasks = mock<TaskContainer> {
            on { register("my", Task::class.java, "foo", "bar") } doReturn taskProvider
        }

        tasks.register<Task>("my", "foo", "bar")

        verify(tasks).register("my", Task::class.java, "foo", "bar")
    }

    @Test
    fun `val task by registering`() {

        val taskProvider = mock<TaskProvider<Task>>()
        val tasks = mock<TaskContainer> {
            on { register("clean") } doReturn taskProvider
        }

        tasks {

            val clean by registering

            inOrder(tasks, taskProvider) {
                verify(tasks).register("clean")
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Task>>()
            )
        }
    }

    @Test
    fun `val task by registering { }`() {

        val taskProvider = mock<TaskProvider<Task>>()
        val tasks = mock<TaskContainer> {
            on { register(eq("clean"), any<Action<Task>>()) } doReturn taskProvider
        }

        tasks {

            val clean by registering {
                assertInferredTypeOf(
                    this,
                    typeOf<Task>()
                )
            }

            inOrder(tasks, taskProvider) {
                verify(tasks).register(eq("clean"), any<Action<Task>>())
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Task>>()
            )
        }
    }

    @Test
    fun `val task by registering(type)`() {

        val taskProvider = mockTaskProviderFor(mock<Delete>())
        val tasks = mock<TaskContainer> {
            on { register("clean", Delete::class.java) } doReturn taskProvider
        }

        tasks {

            val clean by registering(Delete::class)

            inOrder(tasks, taskProvider) {
                verify(tasks).register("clean", Delete::class.java)
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Delete>>()
            )
        }
    }

    @Test
    fun `val task by registering(type) { }`() {

        val taskProvider = mockTaskProviderFor(mock<Delete>())
        val tasks = mock<TaskContainer> {
            onRegisterWithAction("clean", Delete::class, taskProvider)
        }

        tasks {

            val clean by registering(Delete::class) {
                assertInferredTypeOf(
                    this,
                    typeOf<Delete>()
                )
            }

            inOrder(tasks) {
                verify(tasks).register(eq("clean"), eq(Delete::class.java), any<Action<Delete>>())
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Delete>>()
            )
        }
    }

    @Test
    fun `val task by existing { }`() {

        // given:
        val taskProvider = mockTaskProviderFor(mock<Task>())
        val tasks = mock<TaskContainer> {
            on { named("clean") } doReturn taskProvider
        }

        // then:
        tasks {
            // invoke syntax
            val clean by existing {
                assertInferredTypeOf(
                    this,
                    typeOf<Task>()
                )
            }

            inOrder(container, taskProvider) {
                verify(container).named("clean")
                verify(taskProvider).configure(any<Action<Task>>())
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Task>>()
            )
        }

        tasks.apply {
            // regular syntax
            val clean by existing {
            }
            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Task>>()
            )
        }
    }

    @Test
    fun `val task by existing(type)`() {

        // given:
        val task = mock<Delete>()
        val taskProvider = mockTaskProviderFor(task)
        val tasks = mock<TaskContainer> {
            on { named("clean", Delete::class.java) } doReturn taskProvider
        }

        // then:
        tasks {
            // invoke syntax
            val clean by existing(Delete::class)

            inOrder(container, taskProvider) {
                verify(container).named("clean", Delete::class.java)
                verifyNoMoreInteractions()
            }

            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Delete>>()
            )
        }

        tasks.apply {
            // regular syntax
            val clean by existing(Delete::class)
            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Delete>>()
            )
        }
    }

    @Test
    fun `task accessors can be made available via existing delegate provider`() {

        // given:
        val task = mock<Delete>()
        val taskProvider = mockTaskProviderFor(task)
        val tasks = mock<TaskContainer> {
            @Suppress("unchecked_cast")
            on { named("clean") }.thenReturn(taskProvider as TaskProvider<Task>)
            on { named("clean", Delete::class.java) }.thenReturn(taskProvider)
        }

        // when:
        tasks {

            assertInferredTypeOf(this, typeOf<TaskContainerScope>())

            val clean by existing
            assertInferredTypeOf(clean, typeOf<TaskProvider<Task>>())

            clean { // configure
                assertInferredTypeOf(this, typeOf<Task>())
            }

            existing.clean { // configure
                assertInferredTypeOf(this, typeOf<Delete>())
            }
        }

        // then:
        inOrder(tasks, taskProvider, task) {
            verify(tasks, times(1)).named("clean")
            verify(taskProvider, times(1)).configure(any())
            verify(tasks, times(1)).named("clean", Delete::class.java)
            verify(taskProvider, times(1)).configure(any())
            verifyNoMoreInteractions()
        }
    }

    // Hypothetical task accessor
    private
    val ExistingDomainObjectDelegateProvider<out TaskContainer>.clean: TaskProvider<Delete>
        get() = delegateProvider.named<Delete>("clean")
}


internal
fun <T : Task> mockTaskProviderFor(task: T): TaskProvider<T> = mock {
    on { get() } doReturn task
    on { configure(any()) } doAnswer {
        it.getArgument<Action<T>>(0).execute(task)
    }
}


internal
inline fun <reified T> assertInferredTypeOf(@Suppress("UNUSED_PARAMETER") value: T, expectedType: TypeOf<T>) {
    assertThat(typeOf<T>(), equalTo(expectedType))
}
