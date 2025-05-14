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

package org.gradle.initialization.exception;

import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class StackTraceSanitizingExceptionAnalyser implements ExceptionAnalyser {
    private final ExceptionAnalyser analyser;

    public StackTraceSanitizingExceptionAnalyser(ExceptionAnalyser analyser) {
        this.analyser = analyser;
    }

    @Override
    public RuntimeException transform(Throwable failure) {
        return (RuntimeException) StackTraceUtils.deepSanitize(analyser.transform(failure));
    }

    @Nullable
    @Override
    public RuntimeException transform(List<Throwable> failures) {
        RuntimeException result = analyser.transform(failures);
        if (result == null) {
            return null;
        }
        return (RuntimeException) StackTraceUtils.deepSanitize(result);
    }
}
