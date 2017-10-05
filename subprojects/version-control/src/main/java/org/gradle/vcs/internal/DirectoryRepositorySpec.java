/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal;

import org.gradle.vcs.VersionControlSpec;

import java.io.File;

// TODO: Remove this when we have a real Vcs (like Git)
public class DirectoryRepositorySpec implements VersionControlSpec {
    private File sourceDir;

    @Override
    public String getDisplayName() {
        return "dir repo " + sourceDir;
    }

    public File getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(File sourceDir) {
        this.sourceDir = sourceDir;
    }

    @Override
    public String getUniqueId() {
        return "directory-repo:" + sourceDir.getAbsolutePath();
    }

    @Override
    public String getRepoName() {
        return sourceDir.getName();
    }
}
