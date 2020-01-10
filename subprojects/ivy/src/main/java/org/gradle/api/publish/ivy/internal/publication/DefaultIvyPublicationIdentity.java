/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ProjectBackedModule;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class DefaultIvyPublicationIdentity implements IvyPublicationIdentity {
    private final Logger logger;
    private Module delegate;
    private String organisation;
    private String module;
    private String revision;

    public DefaultIvyPublicationIdentity(Module delegate, Logger logger) {
        this.delegate = safeProjectCoordinatesProvider(delegate, logger);
        this.logger = logger;
    }

    public DefaultIvyPublicationIdentity(String organisation, String module, String revision) {
        this.organisation = organisation;
        this.module = module;
        this.revision = revision;
        this.logger = Logging.getLogger(DefaultIvyPublicationIdentity.class);
    }

    @Override
    public String getOrganisation() {
        return organisation != null ? organisation : (delegate != null ? delegate.getGroup() : null);
    }

    @Override
    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    @Override
    public String getModule() {
        return module != null ? module : (delegate != null ? delegate.getName() : null);
    }

    @Override
    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public String getRevision() {
        return revision != null ? revision : (delegate != null ? delegate.getVersion() : null);
    }

    @Override
    public void setRevision(String revision) {
        this.revision = revision;
    }

    private Module safeProjectCoordinatesProvider(Module module, Logger logger) {
        if (module instanceof ProjectBackedModule) {
            return new LazyProjectModuleProvider((ProjectBackedModule) module, logger);
        }
        return module;
    }

    private static final class LazyProjectModuleProvider implements Module {
        private final ProjectBackedModule projectBackedModule;
        private final Logger logger;
        private final AtomicBoolean warned = new AtomicBoolean();

        private LazyProjectModuleProvider(ProjectBackedModule module, Logger logger) {
            this.projectBackedModule = module;
            this.logger = logger;
        }

        @Nullable
        @Override
        public String getProjectPath() {
            return projectBackedModule.getProjectPath();
        }

        @Override
        public String getGroup() {
            maybeWarnAboutDuplicates();
            return projectBackedModule.getGroup();
        }

        @Override
        public String getName() {
            maybeWarnAboutDuplicates();
            return projectBackedModule.getName();
        }

        @Override
        public String getVersion() {
            return projectBackedModule.getVersion();
        }

        @Override
        public String getStatus() {
            return projectBackedModule.getStatus();
        }

        private void maybeWarnAboutDuplicates() {
            if (!warned.getAndSet(true)) {
                Project project = projectBackedModule.getProject();
                List<Project> projectsWithSameId = projectBackedModule.getProjectsWithSameCoordinates();
                if (!projectsWithSameId.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Project ")
                        .append(project.getPath())
                        .append(" has the same (organisation, module name) as ")
                        .append(projectsWithSameId.stream().map(Project::getPath).collect(Collectors.joining(" and ")))
                        .append(". It has been assigned a synthetic Ivy module name of of ")
                        .append(projectBackedModule.getName())
                        .append(" as a workaround, but you should set both the organisation and module name of the publication to be safe.");
                    logger.warn(sb.toString());
                }
            }
        }
    }

}
