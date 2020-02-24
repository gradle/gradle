/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.execution;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.resource.EmptyFileTextResource;
import org.gradle.internal.resource.TextResource;

import java.util.Collection;

public class DeprecateUndefinedBuildWorkExecutor implements BuildWorkExecutor {
    private final BuildWorkExecutor delegate;

    public DeprecateUndefinedBuildWorkExecutor(BuildWorkExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(GradleInternal gradle, Collection<? super Throwable> failures) {
        if (isUndefinedBuild(gradle) && !wasInitTaskRequested(gradle.getStartParameter())) {
            DeprecationLogger.deprecateAction("Executing Gradle tasks as part of an undefined build")
                .willBecomeAnErrorInGradle7()
                .withUpgradeGuideSection(5, "executing_gradle_without_a_settings_file_has_been_deprecated")
                .nagUser();
        }
        delegate.execute(gradle, failures);
    }

    private static boolean isUndefinedBuild(GradleInternal gradle) {
        return !isBuildSrcExecution(gradle) && !gradle.getRootProject().getBuildFile().exists() && isUndefinedResource(gradle.getSettings().getSettingsScript().getResource());
    }

    private static boolean isUndefinedResource(TextResource settingsScript) {
        return settingsScript instanceof EmptyFileTextResource && settingsScript.getFile() == null;
    }

    private static boolean wasInitTaskRequested(StartParameter startParameter) {
        return startParameter.getTaskNames().contains("init");
    }

    private static boolean isBuildSrcExecution(GradleInternal gradle) {
        return gradle.getRootProject().getName().equals("buildSrc") && gradle.getParent() != null;
    }
}
