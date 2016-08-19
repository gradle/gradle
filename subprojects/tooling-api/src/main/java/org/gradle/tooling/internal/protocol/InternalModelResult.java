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

package org.gradle.tooling.internal.protocol;

import java.io.File;
import java.io.Serializable;

/**
 * The internal protocol for transferring {@link org.gradle.tooling.connection.ModelResult}s
 */
public class InternalModelResult<T> implements Serializable {
    final File rootDir;
    final String projectPath;
    final T model;
    final RuntimeException failure;

    public static <T> InternalModelResult<T> model(File rootDir, T model) {
        return new InternalModelResult<T>(rootDir, null, model, null);
    }

    public static <T> InternalModelResult<T> model(File rootDir, String projectPath, T model) {
        return new InternalModelResult<T>(rootDir, projectPath, model, null);
    }

    public static <T> InternalModelResult<T> failure(File rootDir, RuntimeException failure) {
        return new InternalModelResult<T>(rootDir, null, null, failure);
    }

    public static <T> InternalModelResult<T> failure(File rootDir, String projectPath, RuntimeException failure) {
        return new InternalModelResult<T>(rootDir, projectPath, null, failure);
    }

    private InternalModelResult(File rootDir, String projectPath, T model, RuntimeException failure) {
        this.rootDir = rootDir;
        this.projectPath = projectPath;
        this.model = model;
        this.failure = failure;
    }

    public File getRootDir() {
        return rootDir;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public T getModel() {
        return model;
    }

    public RuntimeException getFailure() {
        return failure;
    }
}
