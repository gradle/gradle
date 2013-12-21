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

import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.InvalidUserDataException;

import java.util.List;
import java.util.ArrayList;

public abstract class AbstractProjectSpec implements ProjectSpec {
    public boolean containsProject(ProjectRegistry<?> registry) {
        checkPreconditions(registry);
        for (ProjectIdentifier project : registry.getAllProjects()) {
            if (select(project)) {
                return true;
            }
        }
        return false;
    }

    public <T extends ProjectIdentifier> T selectProject(ProjectRegistry<? extends T> registry) {
        checkPreconditions(registry);
        List<T> matches = new ArrayList<T>();
        for (T project : registry.getAllProjects()) {
            if (select(project)) {
                matches.add(project);
            }
        }
        if (matches.isEmpty()) {
            throw new InvalidUserDataException(formatNoMatchesMessage());
        }
        if (matches.size() != 1) {
            throw new InvalidUserDataException(formatMultipleMatchesMessage(matches));
        }
        return matches.get(0);
    }

    protected void checkPreconditions(ProjectRegistry<?> registry) {
    }

    protected abstract String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches);

    protected abstract String formatNoMatchesMessage();

    protected abstract boolean select(ProjectIdentifier project);
}
