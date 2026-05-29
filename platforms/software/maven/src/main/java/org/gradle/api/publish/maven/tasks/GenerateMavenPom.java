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
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
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
public abstract class GenerateMavenPom extends DefaultTask {

    private final Transient.Var<MavenPom> pom = varOf();
    private final Cached<MavenPomFileGenerator.MavenPomSpec> mavenPomSpec = Cached.of(() ->
        MavenPomFileGenerator.generateSpec((MavenPomInternal) getPom())
    );

    @Inject
    protected abstract FileResolver getFileResolver();

    /**
     * The Maven POM.
     *
     * @return The Maven POM.
     */
    @Internal
    @ToBeReplacedByLazyProperty
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
     * @since 9.7.0
     */
    @Incubating
    @OutputFile
    public abstract RegularFileProperty getDestinationFile();

    /**
     * The file the POM will be written to.
     *
     * @return The file the POM will be written to
     */
    @ReplacedBy("destinationFile")
    @NotToBeReplacedByLazyProperty(because = "Bridge for backward compatibility, use getDestinationFile() instead", willBeDeprecated = true)
    public File getDestination() {
        return getDestinationFile().isPresent() ? getDestinationFile().get().getAsFile() : null;
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * @param destination The file the descriptor will be written to.
     * @since 4.0
     */
    public void setDestination(File destination) {
        getDestinationFile().fileValue(destination);
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * The value is resolved with {@link Project#file(Object)}
     *
     * @param destination The file the descriptor will be written to.
     */
    public void setDestination(Object destination) {
        getDestinationFile().fileValue(getFileResolver().resolve(destination));
    }

    @TaskAction
    public void doGenerate() {
        mavenPomSpec.get().writeTo(getDestinationFile().get().getAsFile());
    }

}
