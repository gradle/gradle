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

import java.util.List;

public final class DefaultJavaAppStartScriptGenerationDetails implements JavaAppStartScriptGenerationDetails {

    private final String applicationName;
    private final String optsEnvironmentVar;
    private final String exitEnvironmentVar;
    private final String mainClassName;
    private final List<String> defaultJvmOpts;
    private final List<String> classpath;
    private final String scriptRelPath;
    private final String appNameSystemProperty;

    public DefaultJavaAppStartScriptGenerationDetails(String applicationName, String optsEnvironmentVar, String exitEnvironmentVar, String mainClassName, List<String> defaultJvmOpts,
                                                      List<String> classpath, String scriptRelPath, String appNameSystemProperty) {
        this.applicationName = applicationName;
        this.optsEnvironmentVar = optsEnvironmentVar;
        this.exitEnvironmentVar = exitEnvironmentVar;
        this.mainClassName = mainClassName;
        this.defaultJvmOpts = defaultJvmOpts;
        this.classpath = classpath;
        this.scriptRelPath = scriptRelPath;
        this.appNameSystemProperty = appNameSystemProperty;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getOptsEnvironmentVar() {
        return optsEnvironmentVar;
    }

    public String getExitEnvironmentVar() {
        return exitEnvironmentVar;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public List<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public List<String> getClasspath() {
        return classpath;
    }

    public String getScriptRelPath() {
        return scriptRelPath;
    }

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

        if (appNameSystemProperty != null ? !appNameSystemProperty.equals(that.appNameSystemProperty) : that.appNameSystemProperty != null) {
            return false;
        }
        if (applicationName != null ? !applicationName.equals(that.applicationName) : that.applicationName != null) {
            return false;
        }
        if (classpath != null ? !classpath.equals(that.classpath) : that.classpath != null) {
            return false;
        }
        if (defaultJvmOpts != null ? !defaultJvmOpts.equals(that.defaultJvmOpts) : that.defaultJvmOpts != null) {
            return false;
        }
        if (exitEnvironmentVar != null ? !exitEnvironmentVar.equals(that.exitEnvironmentVar) : that.exitEnvironmentVar != null) {
            return false;
        }
        if (mainClassName != null ? !mainClassName.equals(that.mainClassName) : that.mainClassName != null) {
            return false;
        }
        if (optsEnvironmentVar != null ? !optsEnvironmentVar.equals(that.optsEnvironmentVar) : that.optsEnvironmentVar != null) {
            return false;
        }
        if (scriptRelPath != null ? !scriptRelPath.equals(that.scriptRelPath) : that.scriptRelPath != null) {
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
        result = 31 * result + (scriptRelPath != null ? scriptRelPath.hashCode() : 0);
        result = 31 * result + (appNameSystemProperty != null ? appNameSystemProperty.hashCode() : 0);
        return result;
    }
}
