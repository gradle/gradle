/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection.state;

import java.util.Map;
import java.util.Set;

/**
 * The persistent state for a single task execution.
 */
public abstract class TaskExecution {
    private String taskClass;
    private Map<String, Object> inputProperties;
    private Set<String> outputFiles;

    public Set<String> getOutputFiles() {
        return outputFiles;
    }

    public void setOutputFiles(Set<String> outputFiles) {
        this.outputFiles = outputFiles;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public void setTaskClass(String taskClass) {
        this.taskClass = taskClass;
    }

    public Map<String, Object> getInputProperties() {
        return inputProperties;
    }

    public void setInputProperties(Map<String, Object> inputProperties) {
        this.inputProperties = inputProperties;
    }

    /**
     * @return May return null.
     */
    public abstract FileCollectionSnapshot getOutputFilesSnapshot();

    public abstract void setOutputFilesSnapshot(FileCollectionSnapshot outputFilesSnapshot);

    /**
     * @return May return null.
     */
    public abstract FileCollectionSnapshot getInputFilesSnapshot();

    public abstract void setInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot);
}
