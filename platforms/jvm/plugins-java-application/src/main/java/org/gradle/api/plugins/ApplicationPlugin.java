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

/**
 * This type exists so that existing plugins that reference it continue to work.
 *
 * All plugins should update to use {@link JavaApplicationPlugin} instead.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public abstract class ApplicationPlugin extends AbstractApplicationPlugin {
    public static final String APPLICATION_PLUGIN_NAME = "application";
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME;
    public static final String TASK_RUN_NAME = "run";
    public static final String TASK_START_SCRIPTS_NAME = "startScripts";
    public static final String TASK_DIST_ZIP_NAME = "distZip";
    public static final String TASK_DIST_TAR_NAME = "distTar";
}
