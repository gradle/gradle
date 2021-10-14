/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.enterprise.test.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.internal.enterprise.test.TestTaskForkOptions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class DefaultTestTaskForkOptions implements TestTaskForkOptions {

    private final File workingDir;
    private final String executable;
    private final int javaMajorVersion;
    private final Iterable<File> classpath;
    private final Iterable<File> modulePath;
    private final List<String> jvmArgs;
    private final Map<String, String> environment;

    DefaultTestTaskForkOptions(
        File workingDir,
        String executable,
        int javaMajorVersion,
        Iterable<File> classpath,
        Iterable<File> modulePath,
        List<String> jvmArgs,
        Map<String, String> environment
    ) {
        this.workingDir = workingDir;
        this.executable = executable;
        this.javaMajorVersion = javaMajorVersion;
        this.classpath = classpath;
        this.modulePath = modulePath;
        this.jvmArgs = ImmutableList.copyOf(jvmArgs);
        this.environment = ImmutableMap.copyOf(environment);
    }

    @Override
    public File getWorkingDir() {
        return workingDir;
    }

    @Override
    public String getExecutable() {
        return executable;
    }

    @Override
    public int getJavaMajorVersion() {
        return javaMajorVersion;
    }

    @Override
    public Stream<File> getClasspath() {
        return StreamSupport.stream(classpath.spliterator(), false);
    }

    @Override
    public Stream<File> getModulePath() {
        return StreamSupport.stream(modulePath.spliterator(), false);
    }

    @Override
    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return environment;
    }
}
