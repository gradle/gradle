/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.worker;

import com.google.common.collect.ImmutableList;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import java.io.File;
import java.net.URL;

/**
 * The classpath of a forked test process, which includes both the application and implementation classpaths.
 * User-defined classes are loaded by and executed in the context of the application classloader. This classloader
 * contains classes from the {@code testRuntimeClasspath}. The implementation classpath includes classes required
 * by Gradle's test framework integrations.
 *
 * <p>In some cases, classes from the application classpath may be accessed by the implementation classpath. These
 * are specified by {@link WorkerProcessBuilder#sharedPackages}, but should likely be tracked in this class as well.</p>
 *
 * <p>This classpath is intended to be consumed by the {@link ForkingTestClassProcessor}.</p>
 */
public class ForkedTestClasspath {
    private final ImmutableList<File> applicationClasspath;
    private final ImmutableList<File> applicationModulepath;
    private final ImmutableList<URL> implementationClasspath;
    public final ImmutableList<URL> implementationModulepath;

    public ForkedTestClasspath(
        ImmutableList<File> applicationClasspath,
        ImmutableList<File> applicationModulepath,
        ImmutableList<URL> implementationClasspath,
        ImmutableList<URL> implementationModulepath
    ) {
        this.applicationClasspath = applicationClasspath;
        this.applicationModulepath = applicationModulepath;
        this.implementationClasspath = implementationClasspath;
        this.implementationModulepath = implementationModulepath;
    }

    public ImmutableList<File> getApplicationClasspath() {
        return applicationClasspath;
    }

    public ImmutableList<File> getApplicationModulepath() {
        return applicationModulepath;
    }

    public ImmutableList<URL> getImplementationClasspath() {
        return implementationClasspath;
    }

    public ImmutableList<URL> getImplementationModulepath() {
        return implementationModulepath;
    }
}
