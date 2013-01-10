/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal;

import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultMavenPublication implements MavenPublicationInternal {

    private final String name;
    private final MavenPomInternal pom;
    private final MavenProjectIdentity projectIdentity;
    private final File pomDir;
    private SoftwareComponentInternal component;
    private final MavenArtifactSet mavenArtifacts = new MavenArtifactSet();

    public DefaultMavenPublication(
            String name, Instantiator instantiator, MavenProjectIdentity projectIdentity, File pomDir
    ) {
        this.name = name;
        this.pom = instantiator.newInstance(DefaultMavenPom.class);
        this.projectIdentity = projectIdentity;
        this.pomDir = pomDir;
    }

    public String getName() {
        return name;
    }

    public MavenPomInternal getPom() {
        return pom;
    }

    public void pom(Action<? super MavenPom> configure) {
        configure.execute(pom);
    }

    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException("A MavenPublication cannot include multiple components");
        }
        this.component = (SoftwareComponentInternal) component;

        // TODO:DAZ This isn't lazy enough. Entire PublishArtifactSet should be in MavenArtifactSet, preferably.
        for (PublishArtifact publishArtifact : this.component.getArtifacts()) {
            artifact(publishArtifact);
        }
    }

    public void artifact(Object source) {
        if (source instanceof AbstractArchiveTask) {
            mavenArtifacts.add(new ArchiveTaskAdapter((AbstractArchiveTask) source));
            return;
        }
        if (source instanceof PublishArtifact) {
            mavenArtifacts.add(new PublishArtifactAdapter((PublishArtifact) source));
            return;
        }
        throw new IllegalArgumentException();
    }

    public FileCollection getPublishableFiles() {
        return mavenArtifacts.getFiles();
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        Set<Dependency> runtimeDependencies = component == null ? Collections.<Dependency>emptySet() : component.getRuntimeDependencies();
        return new MavenNormalizedPublication(projectIdentity, mavenArtifacts, runtimeDependencies, pom.getXmlAction());
    }

    public File getPomDir() {
        return pomDir;
    }

    private static class MavenArtifactSet extends DefaultDomainObjectSet<MavenArtifact> {
        private final TaskDependencyInternal builtBy = new ArtifactsTaskDependency();
        private final ArtifactsFileCollection files = new ArtifactsFileCollection();
        public MavenArtifactSet() {
            super(MavenArtifact.class);
        }

        public FileCollection getFiles() {
            return files;
        }

        private class ArtifactsFileCollection extends AbstractFileCollection {

            public String getDisplayName() {
                return "maven artifacts";
            }

            @Override
            public TaskDependency getBuildDependencies() {
                return builtBy;
            }

            public Set<File> getFiles() {
                Set<File> files = new LinkedHashSet<File>();
                for (MavenArtifact artifact : MavenArtifactSet.this) {
                    files.add(artifact.getFile());
                }
                return files;
            }
        }

        private class ArtifactsTaskDependency extends AbstractTaskDependency {
            public void resolve(TaskDependencyResolveContext context) {
                for (MavenArtifact publishArtifact : MavenArtifactSet.this) {
                    context.add(publishArtifact);
                }
            }
        }
    }

    private static class PublishArtifactAdapter implements MavenArtifact, Buildable {
        private final PublishArtifact delegate;

        private PublishArtifactAdapter(PublishArtifact delegate) {
            this.delegate = delegate;
        }

        public String getClassifier() {
            return delegate.getClassifier();
        }

        public String getExtension() {
            return delegate.getExtension();
        }

        public File getFile() {
            return delegate.getFile();
        }

        public TaskDependency getBuildDependencies() {
            return delegate.getBuildDependencies();
        }
    }

    private static class ArchiveTaskAdapter implements MavenArtifact, Buildable {
        private final AbstractArchiveTask delegate;

        private ArchiveTaskAdapter(AbstractArchiveTask delegate) {
            this.delegate = delegate;
        }

        public String getExtension() {
            return delegate.getExtension();
        }

        public String getClassifier() {
            return delegate.getClassifier();
        }

        public File getFile() {
            return delegate.getArchivePath();
        }

        public TaskDependency getBuildDependencies() {
            return new AbstractTaskDependency() {
                public void resolve(TaskDependencyResolveContext context) {
                    context.add(delegate);
                }
            };
        }
    }

}
