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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.internal.protocol.ConnectionParametersVersion1;

import java.io.File;

public class DefaultConnectionParametersVersion1 implements ConnectionParametersVersion1 {
    private final File gradleUserHomeDir;
    private final File projectDir;
    private final Boolean searchUpwards;

    public DefaultConnectionParametersVersion1(File projectDir, File gradleUserHomeDir, Boolean searchUpwards) {
        this.projectDir = projectDir;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.searchUpwards = searchUpwards;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public Boolean isSearchUpwards() {
        return searchUpwards;
    }
}
