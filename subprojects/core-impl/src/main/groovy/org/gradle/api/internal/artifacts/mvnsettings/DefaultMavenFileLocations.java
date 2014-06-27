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

package org.gradle.api.internal.artifacts.mvnsettings;

import org.gradle.api.Nullable;
import org.gradle.internal.SystemProperties;

import java.io.File;

public class DefaultMavenFileLocations implements MavenFileLocations {
    public File getUserMavenDir() {
        return new File(SystemProperties.getUserHome(), ".m2");
    }

    @Nullable
    public File getGlobalMavenDir() {
        String m2Home = System.getenv("M2_HOME");
        if (m2Home == null) {
            return null;
        }
        return new File(m2Home);
    }

    public File getUserSettingsFile() {
        return new File(getUserMavenDir(), "settings.xml");
    }

    @Nullable
    public File getGlobalSettingsFile() {
        File dir = getGlobalMavenDir();
        if (dir == null) {
            return null;
        }
        return new File(dir, "conf/settings.xml");
    }
}