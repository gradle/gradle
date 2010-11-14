/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NestedBuildTracker extends BuildAdapter {
    private final List<GradleInternal> buildStack = new CopyOnWriteArrayList<GradleInternal>();

    @Override
    public void buildStarted(Gradle gradle) {
        buildStack.add(0, (GradleInternal) gradle);
    }

    @Override
    public void buildFinished(BuildResult result) {
        buildStack.remove(result.getGradle());
    }

    public GradleInternal getCurrentBuild() {
        return buildStack.isEmpty() ? null : buildStack.get(0);
    }
}
