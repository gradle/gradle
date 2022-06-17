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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.dependencies.VersionRangeMapper;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.internal.serialization.Transient.varOf;

/**
 * Generates a Maven module descriptor (POM) file.
 *
 * @since 1.4
 */
@UntrackedTask(because = "Gradle doesn't understand the data structures used to configure this task")
public class GenerateMavenPom extends DefaultTask {

    private final Transient.Var<MavenPom> pom = varOf();
    private final Transient.Var<ImmutableAttributes> compileScopeAttributes = varOf(ImmutableAttributes.EMPTY);
    private final Transient.Var<ImmutableAttributes> runtimeScopeAttributes = varOf(ImmutableAttributes.EMPTY);
    private Object destination;
    private final Cached<MavenPomFileGenerator.MavenPomSpec> mavenPomSpec = Cached.of(this::computeMavenPomSpec);

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected VersionRangeMapper getVersionRangeMapper() {
        throw new UnsupportedOperationException();
    }

    public GenerateMavenPom withCompileScopeAttributes(ImmutableAttributes compileScopeAttributes) {
        this.compileScopeAttributes.set(compileScopeAttributes);
        return this;
    }

    public GenerateMavenPom withRuntimeScopeAttributes(ImmutableAttributes runtimeScopeAttributes) {
        this.runtimeScopeAttributes.set(runtimeScopeAttributes);
        return this;
    }

    /**
     * The Maven POM.
     *
     * @return The Maven POM.
     */
    @Internal
    public MavenPom getPom() {
        return pom.get();
    }

    public void setPom(MavenPom pom) {
        this.pom.set(pom);
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
        mavenPomSpec().writeTo(getDestination());
    }

    private MavenPomFileGenerator.MavenPomSpec mavenPomSpec() {
        return mavenPomSpec.get();
    }

    private MavenPomFileGenerator.MavenPomSpec computeMavenPomSpec() {
        MavenPomInternal pomInternal = (MavenPomInternal) getPom();

        MavenPomFileGenerator pomGenerator = new MavenPomFileGenerator(
            pomInternal.getProjectIdentity(),
            getVersionRangeMapper(),
            pomInternal.getVersionMappingStrategy(),
            compileScopeAttributes.get(),
            runtimeScopeAttributes.get(),
            pomInternal.writeGradleMetadataMarker());
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
        for (MavenDependencyInternal optionalDependency : pomInternal.getOptionalDependencies()) {
            pomGenerator.addOptionalDependency(optionalDependency);
        }

        pomGenerator.withXml(pomInternal.getXmlAction());

        return pomGenerator.toSpec();
    }

}
