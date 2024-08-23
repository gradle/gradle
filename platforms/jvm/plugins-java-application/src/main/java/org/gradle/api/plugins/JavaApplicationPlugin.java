/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * <p>A {@link org.gradle.api.Plugin Plugin} which packages and runs a project as a Java Application.</p>
 *
 * <p>The plugin can be configured via the {@link JavaApplication} extension.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/application_plugin.html">Application plugin reference</a>
 * @since 8.11
 */
@Incubating
@SuppressWarnings("deprecation")
public abstract class JavaApplicationPlugin extends ApplicationPlugin implements Plugin<Project> {
    public static final String APPLICATION_PLUGIN_NAME = "java-application";
    public static final String APPLICATION_GROUP = ApplicationPlugin.APPLICATION_GROUP;
    public static final String TASK_RUN_NAME = ApplicationPlugin.TASK_RUN_NAME;
    public static final String TASK_START_SCRIPTS_NAME = ApplicationPlugin.TASK_START_SCRIPTS_NAME;
    public static final String TASK_DIST_ZIP_NAME = ApplicationPlugin.TASK_DIST_ZIP_NAME;
    public static final String TASK_DIST_TAR_NAME = ApplicationPlugin.TASK_DIST_TAR_NAME;
}
