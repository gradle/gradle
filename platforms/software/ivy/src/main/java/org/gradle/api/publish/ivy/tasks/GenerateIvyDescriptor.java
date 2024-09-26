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

package org.gradle.api.publish.ivy.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal;
import org.gradle.api.publish.ivy.internal.tasks.IvyDescriptorFileGenerator;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.serialization.Transient;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@UntrackedTask(because = "Gradle doesn't understand the data structures")
public abstract class GenerateIvyDescriptor extends DefaultTask {

    private final Transient.Var<IvyModuleDescriptorSpec> descriptor = Transient.varOf();
    private final Provider<IvyDescriptorFileGenerator.DescriptorFileSpec> ivyDescriptorSpec = getProject().provider(() ->
        IvyDescriptorFileGenerator.generateSpec(toIvyModuleDescriptorInternal(descriptor.get()))
    );

    /**
     * The module descriptor metadata.
     */
    @NotToBeReplacedByLazyProperty(because = "we need a better way to handle this, see https://github.com/gradle/gradle/pull/30665#pullrequestreview-2329667058")
    @Internal
    public IvyModuleDescriptorSpec getDescriptor() {
        return descriptor.get();
    }

    public void setDescriptor(IvyModuleDescriptorSpec descriptor) {
        this.descriptor.set(descriptor);
    }

    /**
     * The file the descriptor will be written to.
     */
    @OutputFile
    @ReplacesEagerProperty(adapter = GenerateIvyDescriptorAdapter.class)
    public abstract RegularFileProperty getDestination();

    @TaskAction
    public void doGenerate() {
        ivyDescriptorSpec.get().writeTo(getDestination().getAsFile().get());
    }

    private static IvyModuleDescriptorSpecInternal toIvyModuleDescriptorInternal(IvyModuleDescriptorSpec ivyModuleDescriptorSpec) {
        if (ivyModuleDescriptorSpec instanceof IvyModuleDescriptorSpecInternal) {
            return (IvyModuleDescriptorSpecInternal) ivyModuleDescriptorSpec;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "ivyModuleDescriptor implementations must implement the '%s' interface, implementation '%s' does not",
                            IvyModuleDescriptorSpecInternal.class.getName(),
                            ivyModuleDescriptorSpec.getClass().getName()
                    )
            );
        }
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    protected abstract PathToFileResolver getFileResolver();

    static class GenerateIvyDescriptorAdapter {
        @BytecodeUpgrade
        static File getDestination(GenerateIvyDescriptor self) {
            return self.getDestination().getAsFile().getOrNull();
        }

        @BytecodeUpgrade
        static void setDestination(GenerateIvyDescriptor self, File destination) {
            self.getDestination().fileValue(destination);
        }

        @BytecodeUpgrade
        static void setDestination(GenerateIvyDescriptor self, Object destination) {
            ProviderApiDeprecationLogger.logDeprecation(GenerateIvyDescriptor.class, "setDestination(Object)", "getDestination()");
            self.getDestination().fileValue(self.getFileResolver().resolve(destination));
        }
    }
}
