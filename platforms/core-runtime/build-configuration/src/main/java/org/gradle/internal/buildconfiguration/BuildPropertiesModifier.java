/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.buildconfiguration;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public abstract class BuildPropertiesModifier {

    protected File projectDir;

    public BuildPropertiesModifier(File projectDir) {
        this.projectDir = projectDir;
    }

    public File getPropertiesFile() {
        return new File(projectDir, Project.BUILD_PROPERTIES);
    }

    protected void updateProperties(Action<Properties> propertiesAction) {
        File propertiesFile = getPropertiesFile();
        Properties properties = new Properties();
        if (propertiesFile.exists()) {
            properties = GUtil.loadProperties(propertiesFile);
        } else {
            GFileUtils.parentMkdirs(propertiesFile);
        }
        propertiesAction.execute(properties);

        try {
            PropertiesUtils.store(properties, propertiesFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
