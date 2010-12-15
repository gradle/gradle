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
package org.gradle.api.plugins.osgi;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.osgi.DefaultOsgiManifest;
import org.gradle.api.internal.plugins.osgi.OsgiHelper;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.util.ConfigureUtil;

/**
 * Is mixed in into the project when applying the  {@link org.gradle.api.plugins.osgi.OsgiPlugin} .
 *
 * @author Hans Dockter
 */
public class OsgiPluginConvention {
    private Project project;

    public OsgiPluginConvention(Project project) {
        this.project = project;
    }

    /**
     * Creates a new instance of {@link org.gradle.api.plugins.osgi.OsgiManifest}. The returned object is preconfigured with:
     * <pre>
     * version: project.version
     * name: project.archivesBaseName
     * symbolicName: project.group + "." + project.archivesBaseName (see below for exceptions to this rule)
     * </pre>
     *
     * The symbolic name is usually the group + "." + archivesBaseName, with the following exceptions
     * <ul>
     * <li>if group has only one section (no dots) and archivesBaseName is not null then the
     * first package name with classes is returned. eg. commons-logging:commons-logging ->
     * org.apache.commons.logging</li>
     * <li>if archivesBaseName is equal to last section of group then group is returned. eg.
     * org.gradle:gradle -> org.gradle</li>
     * <li>if archivesBaseName starts with last section of group that portion is removed. eg.
     * org.gradle:gradle-core -> org.gradle.core</li>
     * </ul>
     */
    public OsgiManifest osgiManifest() {
        return osgiManifest(null);
    }

    /**
     * Creates and configures a new instance of an  {@link org.gradle.api.plugins.osgi.OsgiManifest} . The closure configures
     * the new manifest instance before it is returned.
     */
    public OsgiManifest osgiManifest(Closure closure) {
        return ConfigureUtil.configure(closure, createDefaultOsgiManifest(project));
    }

    private OsgiManifest createDefaultOsgiManifest(Project project) {
        OsgiHelper osgiHelper = new OsgiHelper();
        OsgiManifest osgiManifest = new DefaultOsgiManifest(((ProjectInternal) project).getFileResolver());
        osgiManifest.setVersion(osgiHelper.getVersion((String) project.property("version")));
        osgiManifest.setName(project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName());
        osgiManifest.setSymbolicName(osgiHelper.getBundleSymbolicName(project));
        return osgiManifest;
    }
}
