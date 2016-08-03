/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.composite;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.composite.CompositeBuildActionParameters;
import org.gradle.internal.composite.CompositeParameters;
import org.gradle.launcher.exec.BuildActionParameters;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

public class DefaultCompositeBuildActionParameters implements BuildActionParameters, CompositeBuildActionParameters, Serializable {
    private final BuildActionParameters delegate;
    private final CompositeParameters compositeParameters;

    public DefaultCompositeBuildActionParameters(BuildActionParameters parameters, CompositeParameters compositeParameters) {
        this.delegate = parameters;
        this.compositeParameters = compositeParameters;
    }

    public CompositeParameters getCompositeParameters() {
        return compositeParameters;
    }

    @Override
    public String toString() {
        return "DefaultCompositeBuildActionParameters{"
            + "params=" + delegate.toString()
            + "compositeParameters=" + compositeParameters
            + '}';
    }

    @Override
    public File getCurrentDir() {
        return delegate.getCurrentDir();
    }

    @Override
    public Map<String, String> getEnvVariables() {
        return delegate.getEnvVariables();
    }

    @Override
    public ClassPath getInjectedPluginClasspath() {
        return delegate.getInjectedPluginClasspath();
    }

    @Override
    public LogLevel getLogLevel() {
        return delegate.getLogLevel();
    }

    @Override
    public Map<String, String> getSystemProperties() {
        return delegate.getSystemProperties();
    }

    @Override
    public boolean isContinuous() {
        return delegate.isContinuous();
    }

    @Override
    public boolean isInteractive() {
        return delegate.isInteractive();
    }

    @Override
    public boolean isUseDaemon() {
        return delegate.isUseDaemon();
    }
}
