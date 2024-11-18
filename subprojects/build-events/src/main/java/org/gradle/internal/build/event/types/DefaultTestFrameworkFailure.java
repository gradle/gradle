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
import org.gradle.tooling.internal.protocol.InternalTestFrameworkFailure;

import java.util.Collections;
import java.util.List;

public class DefaultTestFrameworkFailure extends AbstractTestFailure implements InternalTestFrameworkFailure {

    public DefaultTestFrameworkFailure(Class<? extends Throwable> exceptionType, String message, String description, List<? extends InternalFailure> cause, String className, String stacktrace) {
        super(exceptionType, message, description, cause, className, stacktrace);
    }

    public static DefaultTestFrameworkFailure create(Throwable t, String message, String className, String stacktrace) {
        Throwable cause = t.getCause();
        List<InternalFailure> causeFailure = cause != null && cause != t ? Collections.singletonList(DefaultFailure.fromThrowable(cause)) : Collections.emptyList();
        return new DefaultTestFrameworkFailure(t.getClass(), message, stacktrace, causeFailure, className, stacktrace);
    }
}
