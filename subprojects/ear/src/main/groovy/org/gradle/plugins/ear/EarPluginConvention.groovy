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
package org.gradle.plugins.ear

import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.ConfigureUtil

public class EarPluginConvention {

    /**
     * The name of the application directory, relative to the project directory. Default is "src/main/application".
     */
    String appDirName

    public void setAppDirName(String appDirName) {
        this.appDirName = appDirName
        if (deploymentDescriptor) {
            deploymentDescriptor.readFrom new File(appDirName, "META-INF/" + deploymentDescriptor.fileName)
        }
    }

    /**
     * Allows changing the application directory. Default is "src/main/application".
     *
     * @param appDirName
     */
    void appDirName(String appDirName) {
        this.setAppDirName(appDirName);
    }

    /**
     * The name of the library directory in the EAR file. Default is "lib".
     */
    String libDirName

    /**
     * Allows changing the library directory in the EAR file. Default is "lib".
     */
    void libDirName(String libDirName) {
        this.libDirName = libDirName
    }

    /**
     * A custom deployment descriptor configuration. Default is an "application.xml" with sensible defaults.
     */
    DeploymentDescriptor deploymentDescriptor

    private final FileResolver fileResolver

    def EarPluginConvention(FileResolver fileResolver) {
        this.fileResolver = fileResolver
        deploymentDescriptor = new DefaultDeploymentDescriptor(fileResolver)
        deploymentDescriptor.readFrom "$appDirName/META-INF/$deploymentDescriptor.fileName"
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given closure is executed to configure the deployment descriptor. The {@link DeploymentDescriptor}
     * is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public EarPluginConvention deploymentDescriptor(Closure configureClosure) {
        if (!deploymentDescriptor) {
            deploymentDescriptor = new DefaultDeploymentDescriptor(fileResolver)
        }
        ConfigureUtil.configure(configureClosure, deploymentDescriptor)
        return this
    }
}