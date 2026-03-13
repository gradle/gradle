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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.Transformer;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;

public class MinimalCompilerDaemonForkOptionsConverter implements Transformer<JavaForkOptions, MinimalCompilerDaemonForkOptions> {
    private final JavaForkOptionsFactory forkOptionsFactory;

    public MinimalCompilerDaemonForkOptionsConverter(JavaForkOptionsFactory forkOptionsFactory) {
        this.forkOptionsFactory = forkOptionsFactory;
    }

    @Override
    public JavaForkOptions transform(MinimalCompilerDaemonForkOptions minimalCompilerDaemonForkOptions) {
        JavaForkOptions javaForkOptions = forkOptionsFactory.newJavaForkOptions();
        javaForkOptions.getMinHeapSize().set(minimalCompilerDaemonForkOptions.getMemoryInitialSize());
        javaForkOptions.getMaxHeapSize().set(minimalCompilerDaemonForkOptions.getMemoryMaximumSize());
        javaForkOptions.getJvmArgs().set(minimalCompilerDaemonForkOptions.getJvmArgs());
        return javaForkOptions;
    }
}
