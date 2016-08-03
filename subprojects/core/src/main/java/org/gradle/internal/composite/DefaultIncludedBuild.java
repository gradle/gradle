/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.collect.Maps;

import java.io.File;
import java.util.Map;

public class DefaultIncludedBuild implements IncludedBuild {
    private final File projectDir;
    private final Map<String, String> provided = Maps.newHashMap();

    public DefaultIncludedBuild(File projectDir) {
        this.projectDir = projectDir;
    }

    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public void provides(String moduleId, String projectPath) {
        provided.put(moduleId, projectPath);
    }

    @Override
    public Map<String, String> getProvided() {
        return provided;
    }

    @Override
    public String toString() {
        return String.format("includedBuild[%s]", projectDir.getPath());
    }
}
