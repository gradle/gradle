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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.toSet;

public class DefaultMavenPublication implements MavenPublicationInternal {

    private final String name;
    private final MavenPomInternal pom;
    private final MavenProjectIdentity projectIdentity;
    private final Iterable<PublishArtifact> artifacts;
    private final File pomDir;

    private final FileResolver fileResolver;
    private final TaskResolver taskResolver;

    public DefaultMavenPublication(
            String name, Instantiator instantiator, MavenProjectIdentity projectIdentity, Iterable<PublishArtifact> artifacts, File pomDir,
            FileResolver fileResolver, TaskResolver taskResolver
    ) {
        this.name = name;
        this.pom = instantiator.newInstance(DefaultMavenPom.class);
        this.projectIdentity = projectIdentity;
        this.artifacts = artifacts;
        this.pomDir = pomDir;
        this.fileResolver = fileResolver;
        this.taskResolver = taskResolver;
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

    public FileCollection getPublishableFiles() {
        ConfigurableFileCollection files = new DefaultConfigurableFileCollection("publication artifacts", fileResolver, taskResolver);
        files.from(new Callable<Set<File>>() {
            public Set<File> call() throws Exception {
                return collect(artifacts, new HashSet<File>(), new Transformer<File, PublishArtifact>() {
                    public File transform(PublishArtifact artifact) {
                        return artifact.getFile();
                    }
                });
            }
        });
        files.builtBy(artifacts);
        return files;
    }

    public TaskDependency getBuildDependencies() {
        return getPublishableFiles().getBuildDependencies();
    }

    public MavenNormalizedPublication asNormalisedPublication() {
        return new MavenNormalizedPublication(projectIdentity, artifacts, pom.getXmlAction());
    }

    public File getPomDir() {
        return pomDir;
    }

    public Set<PublishArtifact> getArtifacts() {
        return toSet(artifacts);
    }
}
