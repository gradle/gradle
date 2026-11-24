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

package org.gradle.internal.configuration.problems

import org.gradle.api.problems.internal.location.BeanFrame
import org.gradle.api.problems.internal.location.ClassFrame
import org.gradle.api.problems.internal.location.FileLocationFrame
import org.gradle.api.problems.internal.location.FilePosition
import org.gradle.api.problems.internal.location.Frame
import org.gradle.api.problems.internal.location.ProjectFrame
import org.gradle.api.problems.internal.location.PropertyFrame
import org.gradle.api.problems.internal.location.TaskFrame
import org.gradle.api.problems.internal.location.Trace
import org.gradle.internal.code.Locality


/**
 * Converts this trace into an origin trace of the Problems API, leaf first, or null when the trace says nothing ([PropertyTrace.Unknown] alone).
 *
 * Every kind of [PropertyTrace] becomes a frame. The kinds an IDE can act on get a frame type of their own; the kinds that only
 * Configuration Cache has (lambdas, their captured arguments, virtual properties, system properties) become plain frames with the text
 * Configuration Cache shows for them today. An unknown tail adds no frame: a trace that stops already says the rest is unknown.
 *
 * Locality is unknown throughout, except for the Gradle runtime: a [PropertyTrace] records names, not the classes or the code source behind them.
 */
fun PropertyTrace.toOriginTrace(): Trace? {
    val frames = sequence.flatMap { it.toFrames() }.toList()
    return if (frames.isEmpty()) null else Trace(frames)
}


private
fun PropertyTrace.toFrames(): List<Frame> = when (this) {
    is PropertyTrace.Property -> listOf(PropertyFrame(kind.toString(), name, Locality.UNKNOWN))
    is PropertyTrace.Bean -> listOf(BeanFrame(type.name, Locality.UNKNOWN))
    // A task is "task :path of type T": the type is what someone wrote, the task is the build model unit at the root
    is PropertyTrace.Task -> listOf(ClassFrame(type.name, Locality.UNKNOWN), TaskFrame(path, Locality.UNKNOWN))
    is PropertyTrace.Project -> listOf(ProjectFrame(path, Locality.LOCAL))
    is PropertyTrace.BuildLogic -> listOf(
        filePath?.let { FileLocationFrame(FilePosition(it, lineNumber, null, null, null), Locality.UNKNOWN) }
            ?: Frame(describeAlone(), Locality.UNKNOWN)
    )
    is PropertyTrace.BuildLogicClass -> listOf(ClassFrame(name, Locality.UNKNOWN))
    is PropertyTrace.SerializedLambda -> listOf(Frame("lambda of type $functionalInterfaceClass in $implClass.$implMethodName", Locality.UNKNOWN))
    is PropertyTrace.CapturedLambdaArguments -> listOf(
        Frame(
            when (subkind) {
                PropertyTrace.CapturedLambdaArguments.Subkind.LambdaBody -> "captured state of $owningClass.$owningMethod"
                PropertyTrace.CapturedLambdaArguments.Subkind.BoundReceiver -> "bound receiver of $owningClass.$owningMethod"
            },
            Locality.UNKNOWN
        )
    )
    is PropertyTrace.VirtualProperty -> listOf(Frame(name, Locality.UNKNOWN))
    is PropertyTrace.SystemProperty -> listOf(Frame("system property $name", Locality.UNKNOWN))
    PropertyTrace.Gradle -> listOf(Frame("Gradle runtime", Locality.CORE))
    PropertyTrace.Unknown -> emptyList()
}


private
fun PropertyTrace.describeAlone(): String =
    StructuredMessage.Builder().also { describe(it) }.build().render()
