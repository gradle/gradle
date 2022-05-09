/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.InternalFailure;

import java.io.Serializable;
import java.util.List;

public class AbstractTestFailure implements Serializable {

    private final String message;
    private final String description;
    private final List<? extends InternalFailure> causes;
    private final String className;
    private final String stacktrace;

    protected AbstractTestFailure(String message, String description, List<? extends InternalFailure> causes, String className, String stacktrace) {
        this.message = message;
        this.description = description;
        this.causes = causes;
        this.className = className;
        this.stacktrace = stacktrace;
    }

    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return className;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public List<? extends InternalFailure> getCauses() {
        return causes;
    }
}
