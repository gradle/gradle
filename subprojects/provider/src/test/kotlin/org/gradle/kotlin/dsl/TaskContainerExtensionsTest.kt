package org.gradle.kotlin.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
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

    @Test
    fun `can create tasks with injected constructor arguments`() {

        val tasks = mock<TaskContainer>()

        tasks.create<DefaultTask>("my", "foo", "bar")

        verify(tasks).create("my", DefaultTask::class.java, "foo", "bar")
    }

    @Test
    fun `can access named provider via existing property delegate with type`() {

        // given:
        val cleanTask = mock<Delete>()
        val cleanTaskProvider = mockTaskProviderFor<Task>(cleanTask)
        val tasks = mock<TaskContainer> {
            on { named("clean") } doReturn cleanTaskProvider
        }

        // then:
        tasks {
            // invoke syntax
            val clean by existing(Delete::class)
            assertInferredTypeOf(
                clean,
                typeOf<TaskProvider<Delete>>()
            )

            inOrder(container, cleanTaskProvider) {
                verify(container).named("clean")
                verify(cleanTaskProvider).configure(any<Action<Task>>())
                verifyNoMoreInteractions()
            }
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
        val cleanTask = mock<Delete>()
        val cleanTaskProvider = mockTaskProviderFor<Task>(cleanTask)
        val tasks = mock<TaskContainer> {
            on { named("clean") } doReturn cleanTaskProvider
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
        inOrder(tasks, cleanTaskProvider, cleanTask) {
            verify(tasks, times(2)).named("clean")
            verify(cleanTaskProvider, times(2)).configure(any())
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
inline fun <reified T> assertInferredTypeOf(@Suppress("unused_parameter") value: T, expectedType: TypeOf<*>) {
    assertThat(typeOf<T>(), equalTo(expectedType))
}
