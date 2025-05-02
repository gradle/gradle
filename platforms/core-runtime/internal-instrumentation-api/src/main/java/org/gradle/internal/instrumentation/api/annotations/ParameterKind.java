/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.api.annotations;

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class ParameterKind {
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface Receiver {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface CallerClassName {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface KotlinDefaultMask {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface VarargParameter {
    }

    /**
     * Injects some context from visitor. Not supported for Groovy at the moment.
     *
     * Currently, it's only supported to inject {@link BytecodeInterceptorFilter}.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface InjectVisitorContext {
    }
}
