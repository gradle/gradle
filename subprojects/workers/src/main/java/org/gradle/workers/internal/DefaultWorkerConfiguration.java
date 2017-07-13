/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.util.GUtil;
import org.gradle.workers.ForkMode;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;

import java.io.File;
import java.util.List;

public class DefaultWorkerConfiguration extends DefaultActionConfiguration implements WorkerConfiguration {
    private final JavaForkOptions forkOptions;
    private IsolationMode isolationMode = IsolationMode.AUTO;
    private List<File> classpath = Lists.newArrayList();
    private String displayName;

    public DefaultWorkerConfiguration(FileResolver fileResolver) {
        this.forkOptions = new DefaultJavaForkOptions(fileResolver);
        forkOptions.workingDir(new File("").getAbsoluteFile());
        forkOptions.setEnvironment(Maps.<String, Object>newHashMap());
    }

    @Override
    public Iterable<File> getClasspath() {
        return classpath;
    }

    @Override
    public void setClasspath(Iterable<File> classpath) {
        this.classpath = Lists.newArrayList(classpath);
    }

    @Override
    public IsolationMode getIsolationMode() {
        return isolationMode;
    }

    public void setIsolationMode(IsolationMode isolationMode) {
        this.isolationMode = isolationMode == null ? IsolationMode.AUTO : isolationMode;
    }

    @Override
    public ForkMode getForkMode() {
        switch (isolationMode) {
            case AUTO:
                return ForkMode.AUTO;
            case NONE:
            case CLASSLOADER:
                return ForkMode.NEVER;
            case PROCESS:
                return ForkMode.ALWAYS;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void setForkMode(ForkMode forkMode) {
        switch (forkMode) {
            case AUTO:
                setIsolationMode(IsolationMode.AUTO);
                break;
            case NEVER:
                setIsolationMode(IsolationMode.CLASSLOADER);
                break;
            case ALWAYS:
                setIsolationMode(IsolationMode.PROCESS);
                break;
        }
    }

    @Override
    public JavaForkOptions getForkOptions() {
        return forkOptions;
    }

    @Override
    public void classpath(Iterable<File> files) {
        GUtil.addToCollection(classpath, files);
    }

    @Override
    public void forkOptions(Action<? super JavaForkOptions> forkOptionsAction) {
        forkOptionsAction.execute(forkOptions);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
