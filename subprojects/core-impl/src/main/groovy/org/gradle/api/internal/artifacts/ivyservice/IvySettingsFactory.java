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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.internal.Factory;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;

import java.io.File;

public class IvySettingsFactory implements Factory<IvySettings> {
    private final File gradleUserHome;

    public IvySettingsFactory(File gradleUserHome) {
        this.gradleUserHome = gradleUserHome;
    }

    public IvySettings create() {
        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(new File(gradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME));
        ivySettings.setDefaultCacheIvyPattern(ResolverContainer.DEFAULT_CACHE_IVY_PATTERN);
        ivySettings.setDefaultCacheArtifactPattern(ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        ivySettings.setVariable("ivy.log.modules.in.use", "false");
        ivySettings.setDefaultRepositoryCacheManager(WharfCacheManager.newInstance(ivySettings));
        return ivySettings;
    }
}
