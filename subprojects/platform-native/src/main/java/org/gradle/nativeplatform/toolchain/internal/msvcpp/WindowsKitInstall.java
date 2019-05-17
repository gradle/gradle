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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Named;
import org.gradle.util.VersionNumber;

import java.io.File;

public abstract class WindowsKitInstall implements Named {
    private final File baseDir;
    private final String name;
    private final VersionNumber version;

    public WindowsKitInstall(File baseDir, VersionNumber version, String name) {
        this.baseDir = baseDir;
        this.version = version;
        this.name = name;
    }

    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return name + " " + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WindowsKitInstall component = (WindowsKitInstall) o;

        if (!baseDir.equals(component.baseDir)) {
            return false;
        }
        if (!name.equals(component.name)) {
            return false;
        }
        return version.equals(component.version);

    }

    @Override
    public int hashCode() {
        int result = baseDir.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }
}
