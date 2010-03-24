/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.osgi

import org.gradle.api.Project
import org.gradle.api.internal.plugins.osgi.DefaultOsgiManifest
import org.gradle.api.internal.plugins.osgi.OsgiHelper
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.util.ConfigureUtil

/**
 * @author Hans Dockter
 */
public class OsgiPluginConvention {
    Project project

    def OsgiPluginConvention(Project project) {
        this.project = project
    }

    public OsgiManifest osgiManifest() {
        return osgiManifest(null);
    }

    public OsgiManifest osgiManifest(Closure closure) {
        return ConfigureUtil.configure(closure, createDefaultOsgiManifest(project),
                Closure.DELEGATE_FIRST);
    }

    private OsgiManifest createDefaultOsgiManifest(Project project) {
        OsgiHelper osgiHelper = new OsgiHelper();
        OsgiManifest osgiManifest = new DefaultOsgiManifest(((ProjectInternal) getProject()).fileResolver)
        osgiManifest.setVersion(osgiHelper.getVersion((String) project.property("version")));
        osgiManifest.setName(project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName());
        osgiManifest.setSymbolicName(osgiHelper.getBundleSymbolicName(project));

        return osgiManifest;
    }
}
