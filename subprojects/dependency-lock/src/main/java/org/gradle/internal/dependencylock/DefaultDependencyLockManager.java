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

package org.gradle.internal.dependencylock;

import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.internal.dependencylock.converter.DependencyLockConverter;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.hash.HashUtil;
import org.gradle.util.GFileUtils;

import java.io.File;

public class DefaultDependencyLockManager implements DependencyLockManager {

    private final DependencyLockState dependencyLockState;
    private final DependencyLockConverter dependencyLockConverter;

    public DefaultDependencyLockManager(DependencyLockState dependencyLockState, DependencyLockConverter dependencyLockConverter) {
        this.dependencyLockState = dependencyLockState;
        this.dependencyLockConverter = dependencyLockConverter;
    }

    @Override
    public void lockResolvedDependencies(String projectPath, final String configurationName, ResolutionResult resolutionResult) {
        dependencyLockState.populateFrom(projectPath, configurationName, resolutionResult);
    }

    @Override
    public void writeLock(File lockFile) {
        boolean successDependencyResolution = dependencyLockState.isSuccessfulDependencyResolution();

        if (successDependencyResolution) {
            DependencyLock dependencyLock = dependencyLockState.getDependencyLock();

            if (!dependencyLock.getLocksMapping().isEmpty()) {
                writeLockAndHashFile(dependencyLock, lockFile);
            }
        }
    }

    private void writeLockAndHashFile(DependencyLock dependencyLock, File lockFile) {
        String content = dependencyLockConverter.convert(dependencyLock);
        GFileUtils.mkdirs(lockFile.getParentFile());
        GFileUtils.writeStringToFile(lockFile, content);
        String sha1 = HashUtil.sha1(content.getBytes()).asHexString();
        GFileUtils.writeStringToFile(getHashFile(lockFile), sha1);
    }

    private File getHashFile(File lockFile) {
        return new File(lockFile.getParentFile(), lockFile.getName() + ".sha1");
    }
}
