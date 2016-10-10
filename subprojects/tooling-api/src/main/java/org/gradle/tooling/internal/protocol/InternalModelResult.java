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

import org.gradle.tooling.model.ModelResult;

import java.io.File;
import java.io.Serializable;

/**
 * The internal protocol for transferring {@link ModelResult}s
 */
public class InternalModelResult<T> implements Serializable {
    private File rootDir;
    private String projectPath;
    private T model;
    private RuntimeException failure;

    InternalModelResult(File rootDir, String projectPath, T model, RuntimeException failure) {
        this.rootDir = rootDir;
        this.projectPath = projectPath;
        this.model = model;
        this.failure = failure;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public T getModel() {
        return model;
    }

    public void setModel(T model) {
        this.model = model;
    }

    public RuntimeException getFailure() {
        return failure;
    }

    public void setFailure(RuntimeException failure) {
        this.failure = failure;
    }
}
