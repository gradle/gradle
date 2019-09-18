/*
 * Copyright 2011 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

/**
 * Ear Plugin Convention.
 */
public abstract class EarPluginConvention {
    /**
     * The name of the application directory, relative to the project directory.
     * Default is "src/main/application".
     */
    public abstract String getAppDirName();

    public abstract void setAppDirName(String appDirName);

    /**
     * Allows changing the application directory.
     * Default is "src/main/application".
     */
    public abstract void appDirName(String appDirName);

    /**
     * The name of the library directory in the EAR file.
     * Default is "lib".
     */
    public abstract String getLibDirName();

    public abstract void setLibDirName(String libDirName);

    /**
     * Allows changing the library directory in the EAR file. Default is "lib".
     */
    public abstract void libDirName(String libDirName);

    /**
     * Specifies if the deploymentDescriptor should be generated if it does not exist.
     * Default is true.
     *
     * @since 6.0
     */
    @Incubating
    public abstract Property<Boolean> getGenerateDeploymentDescriptor();

    /**
     * A custom deployment descriptor configuration.
     * Default is an "application.xml" with sensible defaults.
     */
    public abstract DeploymentDescriptor getDeploymentDescriptor();

    public abstract void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor);

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given closure is executed to configure the deployment descriptor.
     * The {@link DeploymentDescriptor} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public abstract EarPluginConvention deploymentDescriptor(Closure configureClosure);

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given action is executed to configure the deployment descriptor.</p>
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    public abstract EarPluginConvention deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction);
}
