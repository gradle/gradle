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
package org.gradle.initialization;

import org.gradle.api.InvalidUserDataException;

import java.io.File;
import java.util.List;

public class ProjectDirectoryProjectSpec extends AbstractProjectSpec {
    private final File dir;

    public ProjectDirectoryProjectSpec(File dir) {
        this.dir = dir;
    }

    @Override
    protected String formatNoMatchesMessage(String settings) {
        return String.format("Project directory '%s' is not part of the build defined by %s.", dir, settings);
    }

    @Override
    protected String formatMultipleMatchesMessage(Iterable<ProjectDescriptorInternal> matches) {
        return String.format("Multiple projects in this build have project directory '%s': %s", dir, matches);
    }

    @Override
    protected void select(ProjectDescriptorRegistry candidates, List<ProjectDescriptorInternal> matches) {
        for (ProjectDescriptorInternal candidate : candidates.getAllProjects()) {
            if (candidate.getProjectDir().equals(dir)) {
                matches.add(candidate);
            }
        }
    }

    @Override
    protected void checkPreconditions(ProjectDescriptorRegistry registry) {
        if (!dir.exists()) {
            throw new InvalidUserDataException(String.format("Project directory '%s' does not exist.", dir));
        }
        if (!dir.isDirectory()) {
            throw new InvalidUserDataException(String.format("Project directory '%s' is not a directory.", dir));
        }
    }
}
