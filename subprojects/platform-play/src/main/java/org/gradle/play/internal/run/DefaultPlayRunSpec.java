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

import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;
import java.io.Serializable;

public class DefaultPlayRunSpec implements PlayRunSpec, Serializable {
    private final Iterable<File> classpath;
    private final File projectPath;
    private BaseForkOptions forkOptions;
    private int httpPort;

    public DefaultPlayRunSpec(Iterable<File> classpath, File projectPath, BaseForkOptions forkOptions, int httpPort) {
        this.classpath = classpath;
        this.projectPath = projectPath;
        this.forkOptions = forkOptions;
        this.httpPort = httpPort;
    }

    public BaseForkOptions getForkOptions() {
        return forkOptions;
    }

    public Iterable<File> getClasspath() {
        return classpath;
    }

    public File getProjectPath() {
        return projectPath;
    }

    public int getHttpPort() {
        return httpPort;
    }
}
