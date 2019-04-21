/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.properties.annotations

import com.google.common.collect.ImmutableList
import org.gradle.api.model.ReplacedBy
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles

import java.lang.annotation.Annotation

interface TaskAnnotations {
    ImmutableList<Class<? extends Annotation>> PROCESSED_PROPERTY_TYPE_ANNOTATIONS = ImmutableList.of(
        Input, InputFile, InputFiles, InputDirectory, Nested, OutputFile, OutputDirectory, OutputFiles, OutputDirectories, Destroys, LocalState
    )

    ImmutableList<Class<? extends Annotation>> UNPROCESSED_PROPERTY_TYPE_ANNOTATIONS = ImmutableList.of(
        Console, ReplacedBy
    )
}
