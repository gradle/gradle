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

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractProjectSpec implements ProjectSpec {
    private static final String UNRELATED_BUILD_HINT = " If this is an unrelated build, it must have its own settings file.";
    @Override
    public boolean containsProject(ProjectDescriptorRegistry registry) {
        checkPreconditions(registry);
        List<ProjectDescriptorInternal> matches = new ArrayList<>();
        select(registry, matches);
        return !matches.isEmpty();
    }

    @Override
    public ProjectDescriptorInternal selectProject(String settingsDescription, ProjectDescriptorRegistry registry) {
        checkPreconditions(registry);
        List<ProjectDescriptorInternal> matches = new ArrayList<>();
        select(registry, matches);
        if (matches.isEmpty()) {
            String message = formatNoMatchesMessage(settingsDescription) + UNRELATED_BUILD_HINT;
            throw new InvalidUserDataException(message);
        }
        if (matches.size() != 1) {
            throw new InvalidUserDataException(formatMultipleMatchesMessage(matches));
        }
        return matches.get(0);
    }

    protected void checkPreconditions(ProjectDescriptorRegistry registry) {
    }

    protected abstract String formatMultipleMatchesMessage(Iterable<ProjectDescriptorInternal> matches);

    protected abstract String formatNoMatchesMessage(String settings);

    protected abstract void select(ProjectDescriptorRegistry candidates, List<ProjectDescriptorInternal> matches);
}
