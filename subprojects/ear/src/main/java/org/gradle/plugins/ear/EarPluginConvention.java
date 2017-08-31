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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.InstantiatorBackedObjectFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;

/**
 * Ear Plugin Convention.
 */
public class EarPluginConvention {

    private FileResolver fileResolver;
    private ObjectFactory objectFactory;

    private DeploymentDescriptor deploymentDescriptor;
    private String appDirName;
    private String libDirName;

    /**
     * Construct an EarPluginConvention using internal {@link Instantiator}.
     *
     * @deprecated Use public {@link ObjectFactory} constructor instead of this one using internal {@link Instantiator}.
     */
    @Deprecated
    public EarPluginConvention(FileResolver fileResolver, Instantiator instantiator) {
        this(fileResolver, new InstantiatorBackedObjectFactory(instantiator));
    }

    /**
     * Construct an EarPluginConvention using public {@link ObjectFactory}.
     *
     * @since 4.2
     */
    @Inject
    public EarPluginConvention(FileResolver fileResolver, ObjectFactory objectFactory) {
        this.fileResolver = fileResolver;
        this.objectFactory = objectFactory;
        deploymentDescriptor = objectFactory.newInstance(DefaultDeploymentDescriptor.class, fileResolver, objectFactory);
        deploymentDescriptor.readFrom("META-INF/application.xml");
        deploymentDescriptor.readFrom(appDirName + "/META-INF/" + deploymentDescriptor.getFileName());
    }

    /**
     * The name of the application directory, relative to the project directory.
     * Default is "src/main/application".
     */
    public String getAppDirName() {
        return appDirName;
    }

    public void setAppDirName(String appDirName) {
        this.appDirName = appDirName;
        if (deploymentDescriptor != null) {
            deploymentDescriptor.readFrom(new File(appDirName, "META-INF/" + deploymentDescriptor.getFileName()));
        }
    }

    /**
     * Allows changing the application directory.
     * Default is "src/main/application".
     */
    public void appDirName(String appDirName) {
        this.setAppDirName(appDirName);
    }

    /**
     * The name of the library directory in the EAR file.
     * Default is "lib".
     */
    public String getLibDirName() {
        return libDirName;
    }

    public void setLibDirName(String libDirName) {
        this.libDirName = libDirName;
    }

    /**
     * Allows changing the library directory in the EAR file. Default is "lib".
     */
    public void libDirName(String libDirName) {
        this.libDirName = libDirName;
    }

    /**
     * A custom deployment descriptor configuration.
     * Default is an "application.xml" with sensible defaults.
     */
    public DeploymentDescriptor getDeploymentDescriptor() {
        return deploymentDescriptor;
    }

    public void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor) {
        this.deploymentDescriptor = deploymentDescriptor;
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given closure is executed to configure the deployment descriptor.
     * The {@link DeploymentDescriptor} is passed to the closure as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return This.
     */
    public EarPluginConvention deploymentDescriptor(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, forceDeploymentDescriptor());
        return this;
    }

    /**
     * Configures the deployment descriptor for this EAR archive.
     *
     * <p>The given action is executed to configure the deployment descriptor.</p>
     *
     * @param configureAction The action.
     * @return This.
     * @since 3.5
     */
    public EarPluginConvention deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction) {
        configureAction.execute(forceDeploymentDescriptor());
        return this;
    }

    private DeploymentDescriptor forceDeploymentDescriptor() {
        if (deploymentDescriptor == null) {
            deploymentDescriptor = objectFactory.newInstance(DefaultDeploymentDescriptor.class, fileResolver, objectFactory);
            assert deploymentDescriptor != null;
        }
        return deploymentDescriptor;
    }
}
