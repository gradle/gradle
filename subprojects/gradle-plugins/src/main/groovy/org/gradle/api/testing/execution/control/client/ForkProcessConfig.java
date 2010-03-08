/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.testing.execution.control.client;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ForkProcessConfig implements Serializable {
    private List<File> sharedClasspath = new ArrayList<File>();
    private List<File> controlClasspath = new ArrayList<File>();
    private List<File> sandboxClasspath = new ArrayList<File>();
    private int pipelineId;
    private int forkId;
    private URI serverAddress;
    private String testFrameworkId;

    public List<File> getControlClasspath() {
        return controlClasspath;
    }

    public void setControlClasspath(List<File> controlClasspath) {
        this.controlClasspath = controlClasspath;
    }

    public int getForkId() {
        return forkId;
    }

    public void setForkId(int forkId) {
        this.forkId = forkId;
    }

    public int getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(int pipelineId) {
        this.pipelineId = pipelineId;
    }

    public List<File> getSandboxClasspath() {
        return sandboxClasspath;
    }

    public void setSandboxClasspath(List<File> sandboxClasspath) {
        this.sandboxClasspath = sandboxClasspath;
    }

    public URI getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(URI serverAddress) {
        this.serverAddress = serverAddress;
    }

    public List<File> getSharedClasspath() {
        return sharedClasspath;
    }

    public void setSharedClasspath(List<File> sharedClasspath) {
        this.sharedClasspath = sharedClasspath;
    }

    public String getTestFrameworkId() {
        return testFrameworkId;
    }

    public void setTestFrameworkId(String testFrameworkId) {
        this.testFrameworkId = testFrameworkId;
    }

    public void shared(File file) {
        sharedClasspath.add(file);
    }

    public void control(File file) {
        controlClasspath.add(file);
    }

    public void sandbox(File file) {
        sandboxClasspath.add(file);
    }
}
