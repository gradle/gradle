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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal;
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates a Maven module descriptor (POM) file.
 *
 * @since 1.5
 */
@SuppressWarnings("this-escape")
@DisableCachingByDefault(because = "Not worth caching")
public abstract class GenerateMavenPom extends DefaultTask {

    public GenerateMavenPom() {
        this.doNotTrackStateIf(
            "withXml actions cannot be snapshotted",
            task -> !((MavenPomInternal) ((GenerateMavenPom) task).getPom().get()).getXmlAction().isEmpty()
        );
    }

    @Inject
    protected abstract FileResolver getFileResolver();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * The Maven POM.
     *
     * @return The Maven POM.
     * @since 1.5
     */
    @Nested
    @ReplacesEagerProperty
    public abstract Property<MavenPom> getPom();

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
     * @since 1.5
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
        getDestinationFile().convention(getObjectFactory().fileProperty().fileValue(destination));
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * The value is resolved with {@link Project#file(Object)}
     *
     * @param destination The file the descriptor will be written to.
     * @since 1.5
     */
    public void setDestination(Object destination) {
        File resolved = getFileResolver().resolve(destination);
        getDestinationFile().fileValue(resolved);
        getDestinationFile().convention(getObjectFactory().fileProperty().fileValue(resolved));
    }

    /**
     * Do generate.
     *
     * @since 1.5
     */
    @TaskAction
    public void doGenerate() {
        MavenPomFileGenerator.generateSpec((MavenPomInternal) getPom().get()).writeTo(getDestinationFile().get().getAsFile());
    }

}
