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

package org.gradle.jvm.application.scripts;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Details for generating Java-based application start scripts.
 */
public interface JavaAppStartScriptGenerationDetails {

    /**
     * The display name of the application
     */
    String getApplicationName();

    /**
     * The environment variable to use to provide additional options to the JVM
     */
    String getOptsEnvironmentVar();

    /**
     * The environment variable to use to control exit value (windows only)
     */
    String getExitEnvironmentVar();

    String getMainClassName();

    List<String> getDefaultJvmOpts();

    /**
     * The classpath, relative to the application home directory.
     */
    List<String> getClasspath();

    /**
     * The module path, relative to the application home directory.
     *
     * @since 6.4
     */
    @Incubating
    List<String> getModulePath();

    /**
     * The path of the script, relative to the application home directory.
     */
    String getScriptRelPath();

    /**
     * This system property to use to pass the script name to the application. May be null.
     */
    @Nullable
    String getAppNameSystemProperty();
}

