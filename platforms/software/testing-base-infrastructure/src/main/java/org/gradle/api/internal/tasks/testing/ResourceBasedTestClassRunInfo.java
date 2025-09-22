/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.jspecify.annotations.NullMarked;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.io.File;

// TODO: Obviously this name is terrible
@NullMarked
public class ResourceBasedTestClassRunInfo implements TestClassRunInfo {
    private final File resourceFile;

    public ResourceBasedTestClassRunInfo(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    public File getTestDefintionFile() {
        return resourceFile;
    }

    @Override
    public DiscoverySelector getDiscoverySelector() {
        return DiscoverySelectors.selectDirectory(resourceFile);
    }

    @Override
    public String getDisplayName() {
        // TODO: Use the relative path from the build's root - make field a RelativeFile?
        return "tests in directory '" + resourceFile.getAbsolutePath() + "'";
    }
}
