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
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.DeprecationLogger;

abstract class AbstractTaskOutputsDeprecatingTaskPropertyBuilder extends AbstractTaskPropertyBuilder implements TaskOutputs {
    protected final TaskOutputs taskOutputs;

    public AbstractTaskOutputsDeprecatingTaskPropertyBuilder(TaskOutputs taskOutputs) {
        this.taskOutputs = taskOutputs;
    }

    private TaskOutputs getTaskOutputs(String method) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Use '%s' on TaskOutputs directly instead.", method));
        return taskOutputs;
    }

    @Override
    public void upToDateWhen(Closure upToDateClosure) {
        getTaskOutputs("upToDateWhen(Closure)").upToDateWhen(upToDateClosure);
    }

    @Override
    public void upToDateWhen(Spec<? super Task> upToDateSpec) {
        getTaskOutputs("upToDateWhen(Spec)").upToDateWhen(upToDateSpec);
    }

    @Override
    public void cacheIf(Spec<? super Task> spec) {
        getTaskOutputs("cacheIf(Spec)").cacheIf(spec);
    }

    @Override
    public void cacheIf(String cachingEnabledReason, Spec<? super Task> spec) {
        getTaskOutputs("cacheIf(String, Spec)").cacheIf(cachingEnabledReason, spec);
    }

    @Override
    public void doNotCacheIf(Spec<? super Task> spec) {
        getTaskOutputs("doNotCacheIf(Spec)").doNotCacheIf(spec);
    }

    @Override
    public void doNotCacheIf(String cachingDisabledReason, Spec<? super Task> spec) {
        getTaskOutputs("doNotCacheIf(String, Spec)").doNotCacheIf(cachingDisabledReason, spec);
    }

    @Override
    public boolean getHasOutput() {
        return getTaskOutputs("getHasOutput()").getHasOutput();
    }

    @Override
    public FileCollection getFiles() {
        return getTaskOutputs("getFiles()").getFiles();
    }

    @Override
    @Deprecated
    public TaskOutputFilePropertyBuilder files(Object... paths) {
        return getTaskOutputs("files(Object...)").files(paths);
    }

    @Override
    @Deprecated
    public TaskOutputFilePropertyBuilder dirs(Object... paths) {
        return getTaskOutputs("dirs(Object...)").dirs(paths);
    }

    @Override
    public TaskOutputFilePropertyBuilder file(Object path) {
        return getTaskOutputs("file(Object)").file(path);
    }

    @Override
    public TaskOutputFilePropertyBuilder dir(Object path) {
        return getTaskOutputs("dir(Object)").dir(path);
    }
}
