/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.rhino;

import org.gradle.api.file.FileCollection;
import org.gradle.process.JavaExecSpec;

public class RhinoExtension {

    public static final String NAME = "rhino";

    public static final String RHINO_SHELL_MAIN = "org.mozilla.javascript.tools.shell.Main";

    public static final String DEFAULT_RHINO_DEPENDENCY_VERSION = "1.7R3";
    public static final String DEFAULT_RHINO_DEPENDENCY_GROUP = "org.mozilla";
    public static final String DEFAULT_RHINO_DEPENDENCY_MODULE = "rhino";

    public static final String CLASSPATH_CONFIGURATION_NAME = "rhinoPluginRhinoClasspath";

    private FileCollection classpath;
    private String version;

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection rhinoClasspath) {
        this.classpath = rhinoClasspath;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void configureJavaExec(JavaExecSpec spec) {
        spec.setMain(RHINO_SHELL_MAIN);
        spec.setClasspath(getClasspath());
    }

}
