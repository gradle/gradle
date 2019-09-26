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
package org.gradle;

import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

/**
 * A {@link BuildListener} adapter class for receiving build events. The methods in this class are empty. This class
 * exists as convenience for creating listener objects.
 */
public class BuildAdapter implements BuildListener {
    @Override
    @Deprecated
    public void buildStarted(Gradle gradle) {
    }

    @Override
    public void beforeSettings(Settings settings) {
    }

    @Override
    public void settingsEvaluated(Settings settings) {
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
    }

    @Override
    public void buildFinished(BuildResult result) {
    }
}

