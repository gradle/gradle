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
package org.gradle.api.internal.project;

import java.io.File;

/**
 * A legacy identifier for a project, used in some legacy software model implementations.
 * <p>
 * Avoid this type if possible. Prefer {@link ProjectIdentity}.
 */
public class ProjectIdentifier {

    private final String projectPath;
    private final File projectDir;

    public ProjectIdentifier(
        String projectPath,
        File projectDir
    ) {
        this.projectPath = projectPath;
        this.projectDir = projectDir;
    }

    public String getPath() {
        return projectPath;
    }

    public File getProjectDir() {
        return projectDir;
    }

}
