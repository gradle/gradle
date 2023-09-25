/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.buildinit.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.resources.TextResource;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsResources;
import org.gradle.util.internal.DistributionLocator;

import static org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsResources.NIGHTLY;
import static org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsResources.RELEASE_CANDIDATE;
import static org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsResources.RELEASE_NIGHTLY;

/**
 * The wrapper plugin.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/gradle_wrapper.html">Gradle Wrapper reference</a>
 */
public abstract class WrapperPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project.getParent() == null) {
            String versionUrl = getVersionUrl();
            TextResourceFactory textFactory = project.getResources().getText();
            TextResource latest = textFactory.fromUri(versionUrl + "/current");
            TextResource releaseCandidate = textFactory.fromUri(versionUrl + "/" + RELEASE_CANDIDATE);
            TextResource nightly = textFactory.fromUri(versionUrl + "/" + NIGHTLY);
            TextResource releaseNightly = textFactory.fromUri(versionUrl + "/" + RELEASE_NIGHTLY);

            project.getTasks().register("wrapper", Wrapper.class, wrapper -> {
                wrapper.setGroup("Build Setup");
                wrapper.setDescription("Generates Gradle wrapper files.");
                wrapper.getNetworkTimeout().convention(10000);
                wrapper.setWrapperVersionsResources(new DefaultWrapperVersionsResources(latest, releaseCandidate, nightly, releaseNightly));
            });
        }
    }

    private static String getVersionUrl() {
        String baseUrl = DistributionLocator.getBaseUrl();
        return baseUrl + "/versions";
    }

}
