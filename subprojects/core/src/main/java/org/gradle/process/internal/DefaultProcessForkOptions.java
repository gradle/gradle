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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.ProcessForkOptions;

import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private final Property<String> executable;
    private final DirectoryProperty workingDir;
    private final MapProperty<String, Object> environment;

    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this.resolver = resolver;
        this.executable = objectFactory.property(String.class);
        Provider<Directory> defaultWorkingDir = objectFactory.directoryProperty()
            .fileProvider(Providers.changing(() -> resolver.resolve(".")));
        this.workingDir = objectFactory.directoryProperty().convention(defaultWorkingDir);
        this.environment = objectFactory.mapProperty(String.class, Object.class)
            .value(Providers.changing(this::getInheritableEnvironment));
    }

    @Override
    public Property<String> getExecutable() {
        return executable;
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        if (executable instanceof Provider) {
            getExecutable().set(((Provider<?>) executable).map(Object::toString));
        } else {
            getExecutable().set(Providers.changing((Providers.SerializableCallable<String>) executable::toString));
        }
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return workingDir;
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        if (dir instanceof Provider) {
            getWorkingDir().fileProvider(((Provider<?>) dir).map(resolver::resolve));
        } else {
            getWorkingDir().set(resolver.resolve(dir));
        }
        return this;
    }

    @Override
    public MapProperty<String, Object> getEnvironment() {
        return environment;
    }

    protected Map<String, ?> getInheritableEnvironment() {
        return System.getenv();
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
        target.getExecutable().set(getExecutable());
        target.getWorkingDir().set(getWorkingDir());
        target.getEnvironment().set(getEnvironment());
        return this;
    }

    @VisibleForTesting
    Map<String, String> getActualEnvironment() {
        return getActualEnvironment(this);
    }

    public static Map<String, String> getActualEnvironment(ProcessForkOptions forkOptions) {
        Map<String, String> actual = new HashMap<>();
        for (Map.Entry<String, Object> entry : forkOptions.getEnvironment().get().entrySet()) {
            actual.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return actual;
    }
}
