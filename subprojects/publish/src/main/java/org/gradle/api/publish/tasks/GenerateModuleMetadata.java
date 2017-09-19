/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.publish.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.publish.internal.MetadataFileGenerator;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Generates a Gradle metadata file to represent a published {@link org.gradle.api.component.SoftwareComponent} instance.
 *
 * @since 4.3
 */
@Incubating
public class GenerateModuleMetadata extends DefaultTask {
    private final PropertyState<ComponentWithVariants> component;
    private final RegularFileVar outputFile;

    public GenerateModuleMetadata() {
        component = getProject().getProviders().property(ComponentWithVariants.class);
        outputFile = newOutputFile();
        // TODO - should be incremental
        getOutputs().upToDateWhen(Specs.<Task>satisfyNone());
    }

    // TODO - this should be an input
    /**
     * Returns the component to generate the metadata file for.
     */
    @Internal
    public PropertyState<ComponentWithVariants> getComponent() {
        return component;
    }

    /**
     * Returns the output file location.
     */
    @OutputFile
    public RegularFileVar getOutputFile() {
        return outputFile;
    }

    @TaskAction
    void run() {
        File file = outputFile.get().getAsFile();
        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf8"));
            try {
                new MetadataFileGenerator(getServices().get(BuildInvocationScopeId.class)).generateTo(component.get(), writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not generate metadata file " + outputFile.getAsFile(), e);
        }
    }
}
