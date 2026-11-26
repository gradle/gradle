/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.plugins.ear;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.gradle.jvm.tasks.JarInputs;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptorInputs;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link Ear}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface EarInputs extends JarInputs {

    /**
     * The name of the library directory in the EAR file.
     *
     * @see Ear#getLibDirName()
     * @since 10.0.0
     */
    Property<String> getLibDirName();

    /**
     * The lazy properties behind the {@link Ear#getDeploymentDescriptor() deployment descriptor} of the task.
     *
     * @since 10.0.0
     */
    @LazyProperties
    DeploymentDescriptorInputs getDeploymentDescriptor();
}
