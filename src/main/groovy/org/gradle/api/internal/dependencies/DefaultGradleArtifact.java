/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.dependencies.GradleArtifact;
import org.gradle.api.internal.dependencies.DependenciesUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultGradleArtifact implements GradleArtifact {
    private String userDescription;

    public DefaultGradleArtifact(String userDescription) {
        this.userDescription = userDescription;
    }

    public Artifact createIvyArtifact(ModuleRevisionId moduleRevisionId) {
        Map<String, String> groups = DependenciesUtil.splitExtension(userDescription);
        String[] coreSplit = groups.get("core").split(":");
        String baseName = coreSplit[0];
        Map extraAttributes = coreSplit.length > 1 ? WrapUtil.toMap(DependencyManager.CLASSIFIER, coreSplit[1]) : new HashMap();
        return new DefaultArtifact(moduleRevisionId, null, baseName, groups.get("extension"), groups.get("extension"),
                extraAttributes);
    }

    public String toString() {
        return String.format("Artifact type=%s value=%s", userDescription.getClass(), userDescription);
    }

    public String getUserDescription() {
        return userDescription;
    }

    public void setUserDescription(String userDescription) {
        this.userDescription = userDescription;
    }
}
