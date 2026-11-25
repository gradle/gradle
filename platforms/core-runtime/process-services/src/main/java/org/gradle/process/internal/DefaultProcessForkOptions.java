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

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableTransformer;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.process.ProcessForkOptions;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private OptionStorage<Object> executable = OptionStorage.inField(null);
    private final DirectoryProperty workingDir;
    @SuppressWarnings("this-escape") // The storage calls back only after construction
    private EnvironmentStorage environment = EnvironmentStorage.inField(this);

    /**
     * Kept for backward compatibility with some external plugins (e.g. KGP). Use {@link #DefaultProcessForkOptions(ObjectFactory, PathToFileResolver)} instead.
     *
     * @deprecated Use {@link #DefaultProcessForkOptions(ObjectFactory, PathToFileResolver)} instead.
     */
    @Deprecated
    public DefaultProcessForkOptions(PathToFileResolver resolver) {
        this(resolver, SimplePropertyFactory.directoryProperty((FileResolver) resolver), SimplePropertyFactory.directoryProperty((FileResolver) resolver));
    }

    @Inject
    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this(resolver, objectFactory.directoryProperty(), objectFactory.directoryProperty());
    }

    private DefaultProcessForkOptions(PathToFileResolver resolver, DirectoryProperty defaultWorkingDir, DirectoryProperty workingDir) {
        this.resolver = resolver;
        this.workingDir = workingDir.convention(defaultWorkingDir.fileProvider(Providers.changing(() -> resolver.resolve("."))));
    }

    @Override
    public String getExecutable() {
        Object value = executable.get();
        return value == null ? null : value.toString();
    }

    @Override
    public void setExecutable(String executable) {
        this.executable.set(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        this.executable.set(executable);
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        setExecutable(executable);
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDirectory() {
        return workingDir;
    }

    @Override
    public File getWorkingDir() {
        return getWorkingDirectory().get().getAsFile();
    }

    @Override
    public void setWorkingDir(File dir) {
        // We call resolver to resolve "." in some scopes
        getWorkingDirectory().set(resolver.resolve(dir));
    }

    @Override
    public void setWorkingDir(Object dir) {
        workingDir(dir);
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        if (dir instanceof DirectoryProperty) {
            getWorkingDirectory().set((DirectoryProperty) dir);
        } else if (dir instanceof Directory) {
            getWorkingDirectory().set((Directory) dir);
        } else if (dir instanceof Provider) {
            getWorkingDirectory().fileProvider(((Provider<?>) dir).map((SerializableTransformer<File, Object>) value -> {
                if (value instanceof FileSystemLocation) {
                    return resolver.resolve(((FileSystemLocation) value).getAsFile());
                } else {
                    return resolver.resolve(value);
                }
            }));
        } else {
            getWorkingDirectory().set(resolver.resolve(dir));
        }
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return environment.get();
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
        environment.set(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        environment.put(name, value);
        return this;
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        environment.putAll(environmentVariables);
        return this;
    }

    /**
     * Stores the executable and the environment in the given lazy properties from now on.
     * The current executable becomes the convention.
     * The current environment, or else the inherited environment read when the property is read, becomes both the convention and the value.
     */
    public void attach(Property<String> executable, MapProperty<String, Object> environment) {
        this.executable = OptionStorage.movedToText(this.executable, executable);
        this.environment = this.environment.movedTo(environment);
    }

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions target) {
        target.setExecutable(executable.get());
        target.getWorkingDirectory().set(getWorkingDirectory());
        target.setEnvironment(getEnvironment());
        return this;
    }

    /**
     * Creates a {@link DirectoryProperty} backed only by a {@link FileResolver}, so that the working directory can be
     * exposed as a lazy property even when no {@link org.gradle.api.model.ObjectFactory} is available (e.g. for the
     * legacy {@code DefaultProcessForkOptions(PathToFileResolver)} constructor).
     */
    @NullMarked
    static class SimplePropertyFactory {
        @SuppressWarnings("deprecation")
        public static DirectoryProperty directoryProperty(FileResolver fileResolver) {
            FileCollectionFactory fileCollectionFactory = new DefaultFileCollectionFactory(
                fileResolver,
                DefaultTaskDependencyFactory.withNoAssociatedProject(),
                new DefaultDirectoryFileTreeFactory(),
                PatternSets.getNonCachingPatternSetFactory(),
                PropertyHost.NO_OP,
                FileSystems.getDefault()
            );
            return new DefaultFilePropertyFactory(
                PropertyHost.NO_OP,
                fileResolver,
                fileCollectionFactory
            ).newDirectoryProperty();
        }
    }
}
