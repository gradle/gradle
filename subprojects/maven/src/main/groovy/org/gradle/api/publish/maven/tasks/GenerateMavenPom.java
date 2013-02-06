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

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.publish.maven.internal.MavenPomInternal;
import org.gradle.api.publish.maven.internal.MavenProjectIdentity;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@Incubating
public class GenerateMavenPom extends DefaultTask {

    private final FileResolver fileResolver;
    private MavenPom pom;
    private Object destination;

    @Inject
    public GenerateMavenPom(FileResolver fileResolver) {
        this.fileResolver = fileResolver;

        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    /**
     * The Maven pom.
     *
     * @return The Maven pom.
     */
    public MavenPom getPom() {
        return pom;
    }

    public void setPom(MavenPom pom) {
        this.pom = pom;
    }

    /**
     * The file the pom will be written to.
     *
     * @return The file the pom will be written to
     */
    @OutputFile
    public File getDestination() {
        return destination == null ? null : fileResolver.resolve(destination);
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * The value is resolved with {@link org.gradle.api.Project#file(Object)}
     *
     * @param destination The file the descriptor will be written to.
     */
    public void setDestination(Object destination) {
        this.destination = destination;
    }

    @TaskAction
    public void doGenerate() {
        MavenPomInternal pomInternal = (MavenPomInternal) getPom();

        MavenPomFileGenerator pomGenerator = new MavenPomFileGenerator();
        copyIdentity(pomInternal.getProjectIdentity(), pomGenerator);
        copyDependencies(pomInternal.getRuntimeDependencies(), pomGenerator);
        pomGenerator.withXml(pomInternal.getXmlAction());

        pomGenerator.writeTo(getDestination());
    }

    private void copyIdentity(MavenProjectIdentity projectIdentity, MavenPomFileGenerator pom) {
        pom.setArtifactId(projectIdentity.getArtifactId());
        pom.setGroupId(projectIdentity.getGroupId());
        pom.setVersion(projectIdentity.getVersion());
        pom.setPackaging(projectIdentity.getPackaging());
    }

    private void copyDependencies(Set<Dependency> runtimeDependencies, MavenPomFileGenerator pom) {
        for (Dependency runtimeDependency : runtimeDependencies) {
            pom.addRuntimeDependency(runtimeDependency);
        }
    }
}
