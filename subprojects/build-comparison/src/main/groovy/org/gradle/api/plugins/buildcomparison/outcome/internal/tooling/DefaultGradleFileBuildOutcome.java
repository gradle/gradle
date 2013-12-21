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

package org.gradle.api.plugins.buildcomparison.outcome.internal.tooling;

import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome;

import java.io.File;

public class DefaultGradleFileBuildOutcome extends DefaultGradleBuildOutcome implements GradleFileBuildOutcome {

    private final File file;
    private final String typeIdentifier;

    public DefaultGradleFileBuildOutcome(String id, String description, String taskPath, File file, String typeIdentifier) {
        super(id, description, taskPath);
        this.file = file;
        this.typeIdentifier = typeIdentifier;
    }

    public File getFile() {
        return file;
    }

    public String getTypeIdentifier() {
        return typeIdentifier;
    }

}
