/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild;

import org.gradle.api.initialization.Settings;

import java.io.File;
import java.util.List;

public class ProjectScope {

    private final String basePath;
    private final Settings settings;
    private final List<File> projectBaseDirs;

    public ProjectScope(String basePath, Settings settings, List<File> projectBaseDirs) {
        this.basePath = basePath;
        this.settings = settings;
        this.projectBaseDirs = projectBaseDirs;
    }

    public void subproject(String projectName) {
        settings.include(projectName);
        File projectDir = new File(settings.getRootDir(), basePath + "/" + projectName);
        projectBaseDirs.add(projectDir);
        settings.project(":" + projectName).setProjectDir(projectDir);
    }
}
