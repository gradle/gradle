/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;

@NonNullApi
abstract class TaskOutputsDeprecationSupport implements TaskOutputs {
    // --- See CompatibilityAdapterForTaskOutputs for an explanation for why these methods are here

    private UnsupportedOperationException failWithUnsupportedMethod(String method) {
        throw new UnsupportedOperationException(String.format("Chaining of the TaskOutputs.%s method is not supported since Gradle 4.0.", method));
    }

    @Override
    public void upToDateWhen(Closure upToDateClosure) {
        throw failWithUnsupportedMethod("upToDateWhen(Closure)");
    }

    @Override
    public void upToDateWhen(Spec<? super Task> upToDateSpec) {
        throw failWithUnsupportedMethod("upToDateWhen(Spec)");
    }

    @Override
    public void cacheIf(Spec<? super Task> spec) {
        throw failWithUnsupportedMethod("cacheIf(Spec)");
    }

    @Override
    public void cacheIf(String cachingEnabledReason, Spec<? super Task> spec) {
        throw failWithUnsupportedMethod("cacheIf(String, Spec)");
    }

    public void doNotCacheIf(Spec<? super Task> spec) {
        throw failWithUnsupportedMethod("doNotCacheIf(Spec)");
    }

    @Override
    public void doNotCacheIf(String cachingDisabledReason, Spec<? super Task> spec) {
        throw failWithUnsupportedMethod("doNotCacheIf(String, Spec)");
    }

    @Override
    public boolean getHasOutput() {
        throw failWithUnsupportedMethod("getHasOutput()");
    }

    @Override
    public FileCollection getFiles() {
        throw failWithUnsupportedMethod("getFiles()");
    }

    @Override
    @Deprecated
    public TaskOutputFilePropertyBuilder files(Object... paths) {
        throw failWithUnsupportedMethod("files(Object...)");
    }

    @Override
    @Deprecated
    public TaskOutputFilePropertyBuilder dirs(Object... paths) {
        throw failWithUnsupportedMethod("dirs(Object...)");
    }

    @Override
    public TaskOutputFilePropertyBuilder file(Object path) {
        throw failWithUnsupportedMethod("file(Object)");
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(Object path) {
        throw failWithUnsupportedMethod("dir(Object)");
    }
}
