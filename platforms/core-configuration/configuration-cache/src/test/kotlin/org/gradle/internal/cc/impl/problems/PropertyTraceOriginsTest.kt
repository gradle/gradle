/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import org.gradle.api.Task
import org.gradle.api.problems.internal.location.BeanFrame
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.FileLocationFrame
import org.gradle.api.problems.internal.location.FilePosition
import org.gradle.api.problems.internal.location.PropertyFrame
import org.gradle.api.problems.internal.location.TaskFrame
import org.gradle.internal.Describables
import org.gradle.internal.code.Locality
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.toOriginTrace
import org.gradle.problems.Location
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class PropertyTraceOriginsTest {

    @Test
    fun `a path through the object graph becomes one frame per step, leaf first, ending in the task`() {
        val trace = PropertyTrace.Property(
            PropertyKind.Field, "client",
            PropertyTrace.Bean(
                PropertyTraceOriginsTest::class.java,
                PropertyTrace.Property(PropertyKind.InputProperty, "service", PropertyTrace.Task(Task::class.java, ":app:acme"))
            )
        ).toOriginTrace()!!

        assertThat(
            trace.frames.map { it.displayName },
            equalTo(listOf("field client", "bean ${PropertyTraceOriginsTest::class.java.name}", "input property service", "class org.gradle.api.Task", "task :app:acme"))
        )
        assertThat(
            trace.frames.map { it.javaClass },
            equalTo(listOf<Class<*>>(PropertyFrame::class.java, BeanFrame::class.java, PropertyFrame::class.java, ClassFrame::class.java, TaskFrame::class.java))
        )
    }

    @Test
    fun `build logic with a known script file becomes a file location with the path, not the display name`() {
        val location = Location(Describables.of("build file 'build.gradle'"), Describables.of("build file 'build.gradle'"), "/work/build.gradle", 7)

        val frame = PropertyTrace.BuildLogic(location).toOriginTrace()!!.frames.single() as FileLocationFrame

        assertThat(frame.position, equalTo(FilePosition("/work/build.gradle", 7, null, null, null)))
    }

    @Test
    fun `kinds only Configuration Cache has become plain frames, and the Gradle runtime is core`() {
        val trace = PropertyTrace.VirtualProperty(
            "onlyIf specs",
            PropertyTrace.SerializedLambda("com.acme.AcmePlugin", "lambda\$apply\$0", "org.gradle.api.Action", "void", PropertyTrace.Gradle)
        ).toOriginTrace()!!

        assertThat(
            trace.frames.map { it.displayName },
            equalTo(listOf("onlyIf specs", "lambda of type org.gradle.api.Action in com.acme.AcmePlugin.lambda\$apply\$0", "Gradle runtime"))
        )
        assertThat(trace.frames.last().locality, equalTo(Locality.CORE))
    }

    @Test
    fun `an unknown trace gives no origin, and an unknown tail adds no frame`() {
        assertThat(PropertyTrace.Unknown.toOriginTrace(), nullValue())
        assertThat(
            PropertyTrace.Property(PropertyKind.Field, "f", PropertyTrace.Unknown).toOriginTrace()!!.frames.map { it.displayName },
            equalTo(listOf("field f"))
        )
    }
}
