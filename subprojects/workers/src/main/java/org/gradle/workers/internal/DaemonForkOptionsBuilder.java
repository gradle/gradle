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

import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.JavaForkOptionsInternal;

import java.io.File;
import java.util.Collections;

public class DaemonForkOptionsBuilder {
    private final JavaForkOptionsInternal javaForkOptions;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private Iterable<File> classpath = Collections.emptyList();
    private Iterable<String> sharedPackages = Collections.emptyList();
    private KeepAliveMode keepAliveMode = KeepAliveMode.DAEMON;

    public DaemonForkOptionsBuilder(JavaForkOptionsFactory forkOptionsFactory) {
        this.forkOptionsFactory = forkOptionsFactory;
        javaForkOptions = forkOptionsFactory.newJavaForkOptions();
    }

    public DaemonForkOptionsBuilder classpath(Iterable<File> classpath) {
        this.classpath = classpath;
        return this;
    }

    public DaemonForkOptionsBuilder sharedPackages(Iterable<String> sharedPackages) {
        this.sharedPackages = sharedPackages;
        return this;
    }

    public DaemonForkOptionsBuilder keepAliveMode(KeepAliveMode keepAliveMode) {
        this.keepAliveMode = keepAliveMode;
        return this;
    }

    public DaemonForkOptionsBuilder javaForkOptions(JavaForkOptions javaForkOptions) {
        javaForkOptions.copyTo(this.javaForkOptions);
        return this;
    }

    public DaemonForkOptions build() {
        return new DaemonForkOptions(buildJavaForkOptions(), classpath, sharedPackages, keepAliveMode);
    }

    private JavaForkOptionsInternal buildJavaForkOptions() {
        return forkOptionsFactory.immutableCopy(javaForkOptions);
    }
}
