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
package org.gradle.api.internal.changedetection;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * The persistent state for a single task execution.
 */
public class TaskExecution implements Serializable {
    private String taskClass;
    private Map<String, Object> inputProperties;
    private FileCollectionSnapshot outputFilesSnapshot;
    private FileCollectionSnapshot inputFilesSnapshot;
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

    public FileCollectionSnapshot getOutputFilesSnapshot() {
        return outputFilesSnapshot;
    }

    public void setOutputFilesSnapshot(FileCollectionSnapshot outputFilesSnapshot) {
        this.outputFilesSnapshot = outputFilesSnapshot;
    }

    public FileCollectionSnapshot getInputFilesSnapshot() {
        return inputFilesSnapshot;
    }

    public void setInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot) {
        this.inputFilesSnapshot = inputFilesSnapshot;
    }
}
