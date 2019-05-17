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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.internal.deprecation.Deprecatable;
import org.gradle.internal.deprecation.LoggingDeprecatable;

import java.io.File;
import java.util.Set;

public class StartParameterInternal extends StartParameter implements Deprecatable {
    private final Deprecatable deprecationHandler = new LoggingDeprecatable();

    @Override
    public StartParameter newInstance() {
        return prepareNewInstance(new StartParameterInternal());
    }

    @Override
    public StartParameter newBuild() {
        return prepareNewBuild(new StartParameterInternal());
    }

    @Override
    public void addDeprecation(String deprecation) {
        deprecationHandler.addDeprecation(deprecation);
    }

    @Override
    public Set<String> getDeprecations() {
        return deprecationHandler.getDeprecations();
    }

    @Override
    public void checkDeprecation() {
        deprecationHandler.checkDeprecation();
    }

    public File getGradleHomeDir() {
        return gradleHomeDir;
    }

    public void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
    }
}
