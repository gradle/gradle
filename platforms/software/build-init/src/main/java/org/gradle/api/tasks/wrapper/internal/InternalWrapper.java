/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.wrapper.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.wrapper.WrapperVersionsResources;
import org.gradle.util.GradleVersion;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class InternalWrapper extends DefaultTask {

    private final GradleVersionResolver gradleVersionResolver = new GradleVersionResolver();

    public void setWrapperVersionsResources(WrapperVersionsResources wrapperVersionsResources) {
        DefaultWrapperVersionsResources defaultWrapperVersionsResources = (DefaultWrapperVersionsResources) wrapperVersionsResources;
        gradleVersionResolver.setTextResources(defaultWrapperVersionsResources.getLatest(),
            defaultWrapperVersionsResources.getReleaseCandidate(),
            defaultWrapperVersionsResources.getNightly(),
            defaultWrapperVersionsResources.getReleaseNightly());
    }

    @Internal
    protected boolean isCurrentVersion() {
        return GradleVersion.current().equals(getResolvedGradleVersion());
    }

    @Internal
    protected GradleVersion getResolvedGradleVersion() {
        return gradleVersionResolver.getGradleVersion();
    }

    protected void setUnresolvedGradleVersion(String gradleVersion) {
        this.gradleVersionResolver.setGradleVersionString(gradleVersion);
    }


}
