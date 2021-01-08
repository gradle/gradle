/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.events.task.internal.java;

import org.gradle.tooling.events.task.java.JavaCompileTaskOperationResult.AnnotationProcessorResult;

import java.time.Duration;

public class DefaultAnnotationProcessorResult implements AnnotationProcessorResult {

    private final String className;
    private final Type type;
    private final Duration duration;

    public DefaultAnnotationProcessorResult(String className, Type type, Duration duration) {
        this.className = className;
        this.type = type;
        this.duration = duration;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

}
