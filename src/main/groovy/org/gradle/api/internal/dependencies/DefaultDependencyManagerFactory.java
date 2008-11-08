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

import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.util.WrapUtil;
import org.gradle.util.GradleUtil;
import org.gradle.initialization.ISettingsFinder;
import org.gradle.CacheUsage;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyManagerFactory implements DependencyManagerFactory {
    private ISettingsFinder settingsFinder;
    private CacheUsage cacheUsage;

    public DefaultDependencyManagerFactory(ISettingsFinder settingsFinder, CacheUsage cacheUsage) {
        this.settingsFinder = settingsFinder;
        this.cacheUsage = cacheUsage;
    }

    public DependencyManager createDependencyManager(Project project) {
        Set<IDependencyImplementationFactory> dependencyImpls = WrapUtil.toSet(
                new ModuleDependencyFactory(),
                new ProjectDependencyFactory());
        File buildResolverDir = new File(settingsFinder.getSettingsDir(), Project.TMP_DIR_NAME + "/" + DependencyManager.BUILD_RESOLVER_NAME);
        if (cacheUsage != CacheUsage.ON) {
            GradleUtil.deleteDir(buildResolverDir);
        }
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                new DefaultIvyFactory(),
                new DependencyFactory(dependencyImpls),
                new DefaultResolverFactory(),
                new DefaultSettingsConverter(),
                new DefaultModuleDescriptorConverter(),
                new DefaultDependencyResolver(new Report2Classpath()),
                new DefaultDependencyPublisher(),
                new BuildResolverHandler(buildResolverDir, new LocalReposCacheHandler()),
                new DefaultExcludeRuleContainer());
        dependencyManager.setProject(project);
        return dependencyManager;
    }

    public ISettingsFinder getSettingsFinder() {
        return settingsFinder;
    }

    public void setSettingsFinder(ISettingsFinder settingsFinder) {
        this.settingsFinder = settingsFinder;
    }
}
