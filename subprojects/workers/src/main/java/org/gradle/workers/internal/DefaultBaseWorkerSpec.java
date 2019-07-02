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

package org.gradle.workers.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.util.GUtil;
import org.gradle.workers.BaseWorkerSpec;
import org.gradle.workers.IsolationMode;

import java.io.File;
import java.util.List;

public class DefaultBaseWorkerSpec implements BaseWorkerSpec {
    protected final JavaForkOptions forkOptions;
    protected IsolationMode isolationMode = IsolationMode.AUTO;
    private List<File> classpath = Lists.newArrayList();
    private String displayName;

    public DefaultBaseWorkerSpec(JavaForkOptionsFactory forkOptionsFactory) {
        this.forkOptions = forkOptionsFactory.newJavaForkOptions();
        getForkOptions().setEnvironment(Maps.<String, Object>newHashMap());
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

    @Override
    public void setIsolationMode(IsolationMode isolationMode) {
        this.isolationMode = isolationMode == null ? IsolationMode.AUTO : isolationMode;
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
