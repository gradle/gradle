/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Project;

import java.io.File;

/**
 * <p>A {@link Convention} used for the WarPlugin.</p>
 *
 * @deprecated Please configure the tasks directly. This class is scheduled for removal in Gradle 9.0.
 */
@Deprecated
public abstract class WarPluginConvention {
    /**
     * Returns the web application directory.
     */
    public abstract File getWebAppDir();

    /**
     * The name of the web application directory, relative to the project directory.
     */
    public abstract String getWebAppDirName();

    public abstract void setWebAppDirName(String webAppDirName);

    public abstract Project getProject();
}
