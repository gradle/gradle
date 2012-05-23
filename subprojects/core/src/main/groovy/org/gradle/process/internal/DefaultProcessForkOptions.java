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
package org.gradle.process.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    private final FileResolver resolver;
    private Object executable;
    private Factory<File> workingDir;
    private final Map<String, Object> environment = new HashMap<String, Object>(Jvm.current().getInheritableEnvironmentVariables(System.getenv()));

    public DefaultProcessForkOptions(FileResolver resolver) {
        this.resolver = resolver;
        workingDir = resolver.resolveLater(".");
    }

    protected FileResolver getResolver() {
        return resolver;
    }

    public String getExecutable() {
        return executable == null ? null : executable.toString();
    }

    public void setExecutable(Object executable) {
        this.executable = executable;
    }

    public ProcessForkOptions executable(Object executable) {
        setExecutable(executable);
        return this;
    }

    public File getWorkingDir() {
        return workingDir.create();
    }

    public void setWorkingDir(Object dir) {
        this.workingDir = resolver.resolveLater(dir);
    }

    public ProcessForkOptions workingDir(Object dir) {
        setWorkingDir(dir);
        return this;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public Map<String, String> getActualEnvironment() {
        Map<String, String> actual = new HashMap<String, String>();
        for (Map.Entry<String, Object> entry : environment.entrySet()) {
            actual.put(entry.getKey(), String.valueOf(entry.getValue().toString()));
        }
        return actual;
    }

    public void setEnvironment(Map<String, ?> environmentVariables) {
        environment.clear();
        environment.putAll(environmentVariables);
    }

    public ProcessForkOptions environment(String name, Object value) {
        environment.put(name, value);
        return this;
    }

    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        environment.putAll(environmentVariables);
        return this;
    }

    public ProcessForkOptions copyTo(ProcessForkOptions target) {
        target.setExecutable(executable);
        target.setWorkingDir(workingDir);
        target.setEnvironment(environment);
        return this;
    }
}
