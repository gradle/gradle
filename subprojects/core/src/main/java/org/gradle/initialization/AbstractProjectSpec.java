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
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectRegistry;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractProjectSpec implements ProjectSpec {
    private static final String UNRELATED_BUILD_HINT = " If this is an unrelated build, it must have its own settings file.";
    @Override
    public boolean containsProject(ProjectRegistry<? extends ProjectIdentifier> registry) {
        checkPreconditions(registry);
        List<ProjectIdentifier> matches = new ArrayList<ProjectIdentifier>();
        select(registry, matches);
        return !matches.isEmpty();
    }

    @Override
    public <T extends ProjectIdentifier> T selectProject(String settingsDescription, ProjectRegistry<? extends T> registry) {
        checkPreconditions(registry);
        List<T> matches = new ArrayList<T>();
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

    protected void checkPreconditions(ProjectRegistry<?> registry) {
    }

    protected abstract String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches);

    protected abstract String formatNoMatchesMessage(String settings);

    protected abstract <T extends ProjectIdentifier> void select(ProjectRegistry<? extends T> candidates, List<? super T> matches);
}
