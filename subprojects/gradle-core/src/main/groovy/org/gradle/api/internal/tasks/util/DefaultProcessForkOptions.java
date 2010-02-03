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
package org.gradle.api.internal.tasks.util;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.tasks.util.ProcessForkOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    private final FileResolver resolver;
    private Object executable;
    private Object workingDir = new File(".").getAbsoluteFile();
    private final Map<String, Object> environment = new HashMap<String, Object>(System.getenv());

    public DefaultProcessForkOptions(FileResolver resolver) {
        this.resolver = resolver == null ? new IdentityFileResolver() : resolver;
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
        return resolver.resolve(workingDir);
    }

    public void setWorkingDir(Object dir) {
        this.workingDir = dir;
    }

    public ProcessForkOptions workingDir(Object dir) {
        setWorkingDir(dir);
        return this;
    }

    public Map<String, String> getEnvironment() {
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

    public void environment(String name, Object value) {
        environment.put(name, value);
    }

    public void environment(Map<String, ?> environmentVariables) {
        environment.putAll(environmentVariables);
    }

    public void copyTo(ProcessForkOptions target) {
        target.setExecutable(executable);
        target.setWorkingDir(workingDir);
        target.setEnvironment(environment);
    }
}
