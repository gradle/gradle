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

import org.gradle.api.internal.provider.DefaultMapProperty;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.provider.views.MapPropertyMapView;
import org.gradle.api.internal.provider.views.NullReplacingMapView;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.ProcessForkOptions;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultProcessForkOptions implements ProcessForkOptions {
    protected final PathToFileResolver resolver;
    private Object executable;
    private File workingDir;
    private final Map<String, Object> environment;

    public DefaultProcessForkOptions(ObjectFactory objectFactory, PathToFileResolver resolver) {
        this(objectFactory.mapProperty(String.class, Object.class), resolver);
    }

    /**
     * Do not use. This is only here to keep binary compatibility with the Kotlin Plugin.
     * @param resolver the resolver
     * @see <a href="https://github.com/JetBrains/kotlin/blob/120b4d48847f9658542b70646875e4950d89fb5f/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/js/testing/KotlinJsTest.kt#L155">KotlinJsTest</a>.
     * @deprecated use {@link DefaultProcessForkOptions#DefaultProcessForkOptions(ObjectFactory, PathToFileResolver)}.
     */
    @Deprecated
    public DefaultProcessForkOptions(PathToFileResolver resolver) {
        this(new DefaultMapProperty<>(PropertyHost.NO_OP, String.class, Object.class), resolver);
    }

    private DefaultProcessForkOptions(MapProperty<String, Object> environment, PathToFileResolver resolver) {
        this.resolver = resolver;
        this.environment = new NullReplacingMapView<>(new MapPropertyMapView<>(environment.value(getInheritableEnvironment())));
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
        if (workingDir == null) {
            workingDir = resolver.resolve(".");
        }
        return workingDir;
    }

    @Override
    public void setWorkingDir(File dir) {
        this.workingDir = resolver.resolve(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        this.workingDir = resolver.resolve(dir);
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
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
        environment.clear();
        environment.putAll(environmentVariables);
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

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions target) {
        target.setExecutable(executable);
        target.setWorkingDir(getWorkingDir());
        target.setEnvironment(getEnvironment());
        return this;
    }

}
