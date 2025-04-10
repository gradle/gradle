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
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.process.ProcessForkOptions;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private Object executable;
    private final DirectoryProperty workingDir;
    private Map<String, Object> environment;

    /**
     * Don't use it! Kept for KGP binary compatibility for version lower than 2.1.20.
     * See: <a href="https://github.com/JetBrains/kotlin/blob/658a2010b15a22583f9841e1a2d4bddf1baac612/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/testing/KotlinJsTest.kt#L158">KotlinJsTest.kt#L158</a>.
     *
     * We can remove it once we stop supporting that version.
     */
    @Deprecated
    public DefaultProcessForkOptions(PathToFileResolver resolver) {
        this(resolver, SimplePropertyFactory.directoryProperty((FileResolver) resolver), SimplePropertyFactory.directoryProperty((FileResolver) resolver));
    }

    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this(resolver, objectFactory.directoryProperty(), objectFactory.directoryProperty());
    }

    private DefaultProcessForkOptions(PathToFileResolver resolver, DirectoryProperty defaultWorkingDir, DirectoryProperty workingDir) {
        this.resolver = resolver;
        this.workingDir = workingDir.convention(defaultWorkingDir.fileProvider(Providers.of(new File("."))));
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
            workingDir.fileProvider(((Provider<?>) dir).map(transformer(file -> {
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

    /**
     * Do not use it, used only for KGP binary compatibility.
     */
    @NullMarked
    @Deprecated
    private static class SimplePropertyFactory {

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
