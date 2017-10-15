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

import com.google.common.collect.Sets;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.GFileUtils;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class SimpleVersionControlSystem implements VersionControlSystem {
    @Override
    public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
        return Sets.<VersionRef>newHashSet(new DefaultVersionRef());
    }

    @Override
    public File populate(File versionDir, VersionRef ref, VersionControlSpec spec) {
        File sourceDir = ((DirectoryRepositorySpec)spec).getSourceDir();
        File workingDir = new File(versionDir, sourceDir.getName());
        File checkoutFlag = new File(workingDir, "checkedout");
        try {
            if (!checkoutFlag.exists()) {
                GFileUtils.copyDirectory(sourceDir, workingDir);
                checkoutFlag.createNewFile();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return workingDir;
    }
}
