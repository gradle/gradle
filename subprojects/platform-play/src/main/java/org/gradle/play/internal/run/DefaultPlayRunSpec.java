/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import com.google.common.collect.Sets;
import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;

public class DefaultPlayRunSpec implements PlayRunSpec, Serializable {
    private final Iterable<File> classpath;
    private final Iterable<File> changingClasspath;
    private final File applicationJar;
    private final File assetsJar;
    private final Iterable<File> assetsDirs;
    private final File projectPath;
    private BaseForkOptions forkOptions;
    private int httpPort;

    public DefaultPlayRunSpec(Iterable<File> classpath, Iterable<File> changingClasspath, File applicationJar, File assetsJar, Iterable<File> assetsDirs, File projectPath, BaseForkOptions forkOptions, int httpPort) {
        this.classpath = Sets.newHashSet(classpath);
        this.changingClasspath = changingClasspath != null ? Sets.newHashSet(changingClasspath) : Collections.<File>emptySet();
        this.applicationJar = applicationJar;
        this.assetsJar = assetsJar;
        this.assetsDirs = assetsDirs;
        this.projectPath = projectPath;
        this.forkOptions = forkOptions;
        this.httpPort = httpPort;
    }

    @Override
    public BaseForkOptions getForkOptions() {
        return forkOptions;
    }

    @Override
    public Iterable<File> getClasspath() {
        return classpath;
    }

    @Override
    public Iterable<File> getChangingClasspath() {
        return changingClasspath;
    }

    @Override
    public File getProjectPath() {
        return projectPath;
    }

    @Override
    public int getHttpPort() {
        return httpPort;
    }

    @Override
    public File getApplicationJar() {
        return applicationJar;
    }

    @Override
    public File getAssetsJar() {
        return assetsJar;
    }

    @Override
    public Iterable<File> getAssetsDirs() {
        return assetsDirs;
    }
}
