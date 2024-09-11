/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public final class DefaultJavaAppStartScriptGenerationDetails implements JavaAppStartScriptGenerationDetails {

    private final String applicationName;
    private final String optsEnvironmentVar;
    private final String exitEnvironmentVar;
    private final String mainClassName;
    private final List<String> defaultJvmOpts;
    private final List<String> classpath;
    private final List<String> modulePath;
    private final String scriptRelPath;
    private final String appNameSystemProperty;

    public DefaultJavaAppStartScriptGenerationDetails(String applicationName, String optsEnvironmentVar, String exitEnvironmentVar, String mainClassName,
                                                      List<String> defaultJvmOpts, List<String> classpath, List<String> modulePath, String scriptRelPath, @Nullable String appNameSystemProperty) {
        this.applicationName = applicationName;
        this.optsEnvironmentVar = optsEnvironmentVar;
        this.exitEnvironmentVar = exitEnvironmentVar;
        this.mainClassName = mainClassName;
        this.defaultJvmOpts = defaultJvmOpts;
        this.classpath = classpath;
        this.modulePath = modulePath;
        this.scriptRelPath = scriptRelPath;
        this.appNameSystemProperty = appNameSystemProperty;
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }

    @Override
    public String getOptsEnvironmentVar() {
        return optsEnvironmentVar;
    }

    @Override
    public String getExitEnvironmentVar() {
        return exitEnvironmentVar;
    }

    @Override
    public String getMainClassName() {
        return mainClassName;
    }

    @Override
    public List<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    @Override
    public List<String> getClasspath() {
        return classpath;
    }

    @Override
    public List<String> getModulePath() {
        return modulePath;
    }

    @Override
    public String getScriptRelPath() {
        return scriptRelPath;
    }

    @Override
    @Nullable
    public String getAppNameSystemProperty() {
        return appNameSystemProperty;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultJavaAppStartScriptGenerationDetails that = (DefaultJavaAppStartScriptGenerationDetails) o;

        if (!Objects.equals(appNameSystemProperty, that.appNameSystemProperty)) {
            return false;
        }
        if (!Objects.equals(applicationName, that.applicationName)) {
            return false;
        }
        if (!Objects.equals(classpath, that.classpath)) {
            return false;
        }
        if (!Objects.equals(modulePath, that.modulePath)) {
            return false;
        }
        if (!Objects.equals(defaultJvmOpts, that.defaultJvmOpts)) {
            return false;
        }
        if (!Objects.equals(exitEnvironmentVar, that.exitEnvironmentVar)) {
            return false;
        }
        if (!Objects.equals(mainClassName, that.mainClassName)) {
            return false;
        }
        if (!Objects.equals(optsEnvironmentVar, that.optsEnvironmentVar)) {
            return false;
        }
        if (!Objects.equals(scriptRelPath, that.scriptRelPath)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = applicationName != null ? applicationName.hashCode() : 0;
        result = 31 * result + (optsEnvironmentVar != null ? optsEnvironmentVar.hashCode() : 0);
        result = 31 * result + (exitEnvironmentVar != null ? exitEnvironmentVar.hashCode() : 0);
        result = 31 * result + (mainClassName != null ? mainClassName.hashCode() : 0);
        result = 31 * result + (defaultJvmOpts != null ? defaultJvmOpts.hashCode() : 0);
        result = 31 * result + (classpath != null ? classpath.hashCode() : 0);
        result = 31 * result + (modulePath != null ? modulePath.hashCode() : 0);
        result = 31 * result + (scriptRelPath != null ? scriptRelPath.hashCode() : 0);
        result = 31 * result + (appNameSystemProperty != null ? appNameSystemProperty.hashCode() : 0);
        return result;
    }
}
