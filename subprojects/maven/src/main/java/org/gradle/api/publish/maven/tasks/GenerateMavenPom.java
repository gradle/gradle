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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publication.maven.internal.VersionRangeMapper;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates a Maven module descriptor (POM) file.
 *
 * @since 1.4
 */
public class GenerateMavenPom extends DefaultTask {

    private MavenPom pom;
    private Object destination;

    public GenerateMavenPom() {
        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionRangeMapper getVersionRangeMapper() {
        throw new UnsupportedOperationException();
    }

    /**
     * The Maven POM.
     *
     * @return The Maven POM.
     */
    @Internal
    public MavenPom getPom() {
        return pom;
    }

    public void setPom(MavenPom pom) {
        this.pom = pom;
    }

    /**
     * The file the POM will be written to.
     *
     * @return The file the POM will be written to
     */
    @OutputFile
    public File getDestination() {
        return destination == null ? null : getFileResolver().resolve(destination);
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * @param destination The file the descriptor will be written to.
     * @since 4.0
     */
    public void setDestination(File destination) {
        this.destination = destination;
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

        MavenPomFileGenerator pomGenerator = new MavenPomFileGenerator(pomInternal.getProjectIdentity(), getVersionRangeMapper());
        pomGenerator.configureFrom(pomInternal);

        for (MavenDependency mavenDependency : pomInternal.getApiDependencyManagement()) {
            pomGenerator.addApiDependencyManagement(mavenDependency);
        }

        for (MavenDependency mavenDependency : pomInternal.getRuntimeDependencyManagement()) {
            pomGenerator.addRuntimeDependencyManagement(mavenDependency);
        }

        for (MavenDependency mavenDependency : pomInternal.getImportDependencyManagement()) {
            pomGenerator.addImportDependencyManagement(mavenDependency);
        }

        for (MavenDependencyInternal runtimeDependency : pomInternal.getApiDependencies()) {
            pomGenerator.addApiDependency(runtimeDependency);
        }
        for (MavenDependencyInternal runtimeDependency : pomInternal.getRuntimeDependencies()) {
            pomGenerator.addRuntimeDependency(runtimeDependency);
        }

        pomGenerator.withXml(pomInternal.getXmlAction());

        pomGenerator.writeTo(getDestination());
    }

}
