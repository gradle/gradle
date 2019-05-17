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

import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectRegistry;

import java.io.File;
import java.util.List;

public class CurrentDirectoryProjectSpec extends AbstractProjectSpec {
    private final boolean useRootWhenNoMatch;
    private final File currentDir;

    public CurrentDirectoryProjectSpec(File currentDir, SettingsInternal settings) {
        this.currentDir = currentDir;
        this.useRootWhenNoMatch = currentDir.equals(settings.getSettingsDir());
    }

    @Override
    protected <T extends ProjectIdentifier> void select(ProjectRegistry<? extends T> candidates, List<? super T> matches) {
        for (T candidate : candidates.getAllProjects()) {
            if (candidate.getProjectDir().equals(currentDir)) {
                matches.add(candidate);
            }
        }
        if (useRootWhenNoMatch && matches.isEmpty()) {
            matches.add(candidates.getRootProject());
        }
    }

    @Override
    protected String formatNoMatchesMessage(String settings) {
        return String.format("Project directory '%s' is not part of the build defined by %s.",  currentDir, settings);
    }

    @Override
    protected String formatMultipleMatchesMessage(Iterable<? extends ProjectIdentifier> matches) {
        return String.format("Multiple projects in this build have project directory '%s': %s", currentDir, matches);
    }
}
