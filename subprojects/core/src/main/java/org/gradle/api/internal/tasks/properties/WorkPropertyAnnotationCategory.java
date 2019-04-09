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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Destroys;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.reflect.PropertyAnnotationCategory;
import org.gradle.work.Incremental;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.Map;

public enum WorkPropertyAnnotationCategory implements PropertyAnnotationCategory {
    TYPE(
        Console.class,
        Destroys.class,
        Inject.class,
        Input.class,
        InputArtifact.class,
        InputArtifactDependencies.class,
        InputDirectory.class,
        InputFile.class,
        InputFiles.class,
        LocalState.class,
        Nested.class,
        OptionValues.class,
        OutputDirectories.class,
        OutputDirectory.class,
        OutputFile.class,
        OutputFiles.class,
        ReplacedBy.class
    ),
    INCREMENTAL(
        Incremental.class,
        SkipWhenEmpty.class
    ),
    NORMALIZATION(
        Classpath.class,
        CompileClasspath.class,
        PathSensitive.class
    ),
    OPTIONAL(
        Optional.class
    );

    private final ImmutableSet<Class<? extends Annotation>> annotations;

    WorkPropertyAnnotationCategory(Class<? extends Annotation>... annotations) {
        this.annotations = ImmutableSet.copyOf(annotations);
    }

    @Override
    public String getDisplayName() {
        return name().toLowerCase();
    }

    public static Map<Class<? extends Annotation>, WorkPropertyAnnotationCategory> asMap() {
        ImmutableMap.Builder<Class<? extends Annotation>, WorkPropertyAnnotationCategory> builder = ImmutableMap.builder();
        for (WorkPropertyAnnotationCategory category : values()) {
            for (Class<? extends Annotation> annotation : category.annotations) {
                builder.put(annotation, category);
            }
        }
        return builder.build();
    }
}
