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
package org.gradle.api.plugins

import org.gradle.api.Project

public class WarPluginConvention {
    /**
     * The name of the web application directory, relative to the project directory.
     */
    String webAppDirName
    final Project project

    def WarPluginConvention(Project project) {
        this.project = project
        webAppDirName = 'src/main/webapp'
    }

    /**
     * Returns the web application directory.
     */
    File getWebAppDir() {
        project.file(webAppDirName)
    }
}