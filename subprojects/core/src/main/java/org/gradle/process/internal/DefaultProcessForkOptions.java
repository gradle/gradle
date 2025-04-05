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

import com.google.common.collect.Maps;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private Object executable;
    private final DirectoryProperty workingDir;
    private Map<String, Object> environment;

    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this.resolver = resolver;
        // TODO: Gradle 9.0 we should just use defaultWorkingDir.fileProvider(Providers.of(new File("."))) here,
        //  but it seems ObjectFactory.directoryProperty() doesn't have the right fileResolver set up in some cases
        DirectoryProperty defaultWorkingDir = objectFactory.directoryProperty().fileProvider(Providers.changing(() -> new File(".")));
        this.workingDir = objectFactory.directoryProperty().convention(defaultWorkingDir);
    }

    @Override
    public String getExecutable() {
        return executable == null ? null : executable.toString();
    }

    @Override
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    @Override
    public void setExecutable(Object executable) {
        this.executable = executable;
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        setExecutable(executable);
        return this;
    }

    @Override
    public File getWorkingDir() {
        return workingDir.getAsFile().get();
    }

    @Override
    public void setWorkingDir(File dir) {
        this.workingDir.set(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        if (dir instanceof DirectoryProperty) {
            workingDir.set((DirectoryProperty) dir);
        } else if (dir instanceof Provider) {
            workingDir.fileProvider(((Provider<?>) dir).map(SerializableLambdas.transformer(file -> {
                if (file instanceof FileSystemLocation) {
                    return ((Directory) file).getAsFile();
                } else if (file instanceof File) {
                    return (File) file;
                } else {
                    return resolver.resolve(file);
                }
            })));
        } else if (dir instanceof File) {
            workingDir.set((File) dir);
        } else {
            workingDir.set(resolver.resolve(dir));
        }
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        if (environment == null) {
            setEnvironment(getInheritableEnvironment());
        }
        return environment;
    }

    protected Map<String, ?> getInheritableEnvironment() {
        return System.getenv();
    }

    public Map<String, String> getActualEnvironment() {
        return getActualEnvironment(this);
    }

    public static Map<String, String> getActualEnvironment(ProcessForkOptions forkOptions) {
        Map<String, String> actual = new HashMap<>();
        for (Map.Entry<String, Object> entry : forkOptions.getEnvironment().entrySet()) {
            actual.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return actual;
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        environment = Maps.newHashMap(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        getEnvironment().put(name, value);
        return this;
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        getEnvironment().putAll(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions target) {
        target.setExecutable(executable);
        target.setWorkingDir(getWorkingDir());
        target.setEnvironment(getEnvironment());
        return this;
    }
}
