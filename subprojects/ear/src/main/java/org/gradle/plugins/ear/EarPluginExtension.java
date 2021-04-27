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

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

/**
 * Ear Plugin Extension.
 *
 * @since 7.1
 */
public interface EarPluginExtension {
    /**
     * The name of the application directory, relative to the project directory.
     * Default is "src/main/application".
     */
    String getAppDirName();

    void setAppDirName(String appDirName);

    /**
     * Allows changing the application directory.
     * Default is "src/main/application".
     */
    void appDirName(String appDirName);

    /**
     * The name of the library directory in the EAR file.
     * Default is "lib".
     */
    String getLibDirName();

    void setLibDirName(String libDirName);

    /**
     * Allows changing the library directory in the EAR file. Default is "lib".
     */
    void libDirName(String libDirName);

    /**
     * Specifies if the deploymentDescriptor should be generated if it does not exist.
     * Default is true.
     *
     */
    Property<Boolean> getGenerateDeploymentDescriptor();

    /**
     * A custom deployment descriptor configuration.
     * Default is an "application.xml" with sensible defaults.
     */
    DeploymentDescriptor getDeploymentDescriptor();

    void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor);

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given action is executed to configure the deployment descriptor.</p>
     *
     * @param configureAction The action.
     * @return This.
     */
    EarPluginExtension deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction);
}
