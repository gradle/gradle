/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.configuration;

import groovy.lang.Script;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.internal.Permits;

public class ProjectScriptTarget implements ScriptTarget {
    private final ProjectInternal target;

    public ProjectScriptTarget(ProjectInternal target) {
        this.target = target;
    }

    @Override
    public PluginManagerInternal getPluginManager() {
        return target.getPluginManager();
    }

    @Override
    public String getId() {
        return "proj";
    }

    @Override
    public String getClasspathBlockName() {
        return "buildscript";
    }

    @Override
    public boolean getSupportsPluginsBlock() {
        return true;
    }

    @Override
    public boolean getSupportsPluginManagementBlock() {
        return false;
    }

    @Override
    public boolean getSupportsMethodInheritance() {
        return true;
    }

    @Override
    public Class<? extends BasicScript> getScriptClass() {
        return ProjectScript.class;
    }

    @Override
    public void attachScript(Script script) {
        target.setScript(script);
    }

    @Override
    public void addConfiguration(Runnable runnable, boolean deferrable) {
        if (deferrable) {
            target.addDeferredConfiguration(runnable);
        } else {
            runnable.run();
        }
    }

    @Override
    public Permits getPluginsBlockPermits() {
        VersionCatalogsExtension versionCatalogs = target.getExtensions().findByType(VersionCatalogsExtension.class);
        if (versionCatalogs != null) {
            return new Permits(versionCatalogs.getCatalogNames());
        }
        return Permits.none();
    }
}
