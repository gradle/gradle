/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon.registry;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;

@ServiceScope(Scope.Global.class)
public class DaemonDir {
    private final File baseDir;
    private final File versionedDir;
    private final File registryFile;

    public DaemonDir(File baseDir) {
        this.baseDir = baseDir;
        this.versionedDir = new File(baseDir, GradleVersion.current().getVersion());
        this.registryFile = new File(versionedDir, "registry.bin");
        GFileUtils.mkdirs(this.versionedDir);
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File getVersionedDir() {
        return versionedDir;
    }

    public File getRegistry() {
        return registryFile;
    }
}
