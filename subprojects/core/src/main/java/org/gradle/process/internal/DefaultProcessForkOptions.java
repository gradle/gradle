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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.provider.DefaultMapProperty;
import org.gradle.api.internal.provider.DefaultProperty;
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

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private final Property<String> executable;
    private final DirectoryProperty workingDir;
    private final MapProperty<String, Object> environment;

    /**
     * Don't use! Kept for KGP binary compatibility, will be removed in Gradle 9.
     */
    @Deprecated
    public DefaultProcessForkOptions(PathToFileResolver resolver) {
        this(
            resolver,
            SimplePropertyFactory.property(String.class),
            SimplePropertyFactory.directoryProperty((FileResolver) resolver),
            SimplePropertyFactory.directoryProperty((FileResolver) resolver),
            SimplePropertyFactory.mapProperty(String.class, Object.class)
        );
    }

    @Inject
    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this(
            resolver,
            objectFactory.property(String.class),
            objectFactory.directoryProperty(),
            objectFactory.directoryProperty(),
            objectFactory.mapProperty(String.class, Object.class)
        );
    }

    private DefaultProcessForkOptions(
        PathToFileResolver resolver,
        Property<String> executable,
        DirectoryProperty defaultWorkingDir,
        DirectoryProperty workingDir,
        MapProperty<String, Object> environment
    ) {
        this.resolver = resolver;
        this.executable = executable;
        this.workingDir = workingDir.convention(defaultWorkingDir.fileProvider(Providers.changing(() -> resolver.resolve("."))));
        this.environment = environment.value(Providers.changing(this::getInheritableEnvironment));
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

    /**
     * Do not use it, used only for KGP binary compatibility.
     */
    @Deprecated
    private static class SimplePropertyFactory {

        public static Property<String> property(Class<String> type) {
            return new DefaultProperty<>(PropertyHost.NO_OP, type);
        }

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

        public static MapProperty<String, Object> mapProperty(Class<String> keyType, Class<Object> valueType) {
            return new DefaultMapProperty<>(PropertyHost.NO_OP, keyType, valueType);
        }
    }
}
