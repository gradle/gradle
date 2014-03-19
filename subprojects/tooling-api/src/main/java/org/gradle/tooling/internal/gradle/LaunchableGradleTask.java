/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.gradle;

import org.gradle.tooling.internal.protocol.InternalLaunchable;

import java.io.File;
import java.io.Serializable;

public class LaunchableGradleTask extends DefaultGradleTask implements Serializable, InternalLaunchable {
    // TODO(radimk): move to provider?
    // TODO(radimk): unsupported method for getProject() if not set + test

    public PartialGradleProject getProject() {
        return project;
    }

    public LaunchableGradleTask setProject(PartialGradleProject project) {
        this.project = project;
        return this;
    }

    public String getTaskName() {
        return path;
    }

    public File getProjectDir() {
        return null;
    }

    public String getProjectPath() {
        return null;
    }

    @Override
    public String toString() {
        return "LaunchableGradleTask{"
                + "name='" + name + '\''
                + '}';
    }
}
