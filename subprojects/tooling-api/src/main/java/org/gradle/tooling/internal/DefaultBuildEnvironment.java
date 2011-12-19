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

package org.gradle.tooling.internal;

import org.gradle.tooling.internal.protocol.InternalBuildEnvironment;
import org.gradle.tooling.model.BuildEnvironment;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * by Szczepan Faber, created at: 12/17/11
 */
public class DefaultBuildEnvironment implements BuildEnvironment, InternalBuildEnvironment, Serializable {

    private final String gradleVersion;
    private final File javaHome;
    private final List<String> jvmArguments;

    public DefaultBuildEnvironment(String gradleVersion, File javaHome, List<String> jvmArguments) {
        this.gradleVersion = gradleVersion;
        this.javaHome = javaHome;
        this.jvmArguments = jvmArguments;
    }

    public String getGradleVersion() {
        return gradleVersion;
    }

    public File getJavaHome() {
        return javaHome;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public String getName() {
        return "Build environment information";
    }

    public String getDescription() {
        return null;
    }

    //TODO SF - figure out a way of getting rid of such methods
    //Unfortunately, at this moment ProjectVersion3 is bolted into a lot of classes
    //e.g. all models available from tooling api need to inherit from ProjectVersion3 at the moment
    public String getPath() {
        throw new UnsupportedOperationException("Build environment does not provide 'path' information");
    }

    public File getProjectDirectory() {
        throw new UnsupportedOperationException("Build environment does not provide 'project directory' information");
    }
}
