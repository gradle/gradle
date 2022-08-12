/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.ear.internal;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor;
import org.gradle.util.internal.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.api.reflect.TypeOf.typeOf;

@Deprecated
public class DefaultEarPluginConvention extends org.gradle.plugins.ear.EarPluginConvention implements HasPublicType {
    private ObjectFactory objectFactory;

    private DeploymentDescriptor deploymentDescriptor;
    private String appDirName;
    private String libDirName;
    private final Property<Boolean> generateDeploymentDescriptor;

    @Inject
    public DefaultEarPluginConvention(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        deploymentDescriptor = objectFactory.newInstance(DefaultDeploymentDescriptor.class);
        deploymentDescriptor.readFrom("META-INF/application.xml");
        deploymentDescriptor.readFrom(appDirName + "/META-INF/" + deploymentDescriptor.getFileName());
        generateDeploymentDescriptor = objectFactory.property(Boolean.class);
        generateDeploymentDescriptor.convention(true);
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.plugins.ear.EarPluginConvention.class);
    }

    @Override
    public String getAppDirName() {
        return appDirName;
    }

    @Override
    public void setAppDirName(String appDirName) {
        this.appDirName = appDirName;
        if (deploymentDescriptor != null) {
            deploymentDescriptor.readFrom(new File(appDirName, "META-INF/" + deploymentDescriptor.getFileName()));
        }
    }

    @Override
    public void appDirName(String appDirName) {
        this.setAppDirName(appDirName);
    }

    @Override
    public String getLibDirName() {
        return libDirName;
    }

    @Override
    public void setLibDirName(String libDirName) {
        this.libDirName = libDirName;
    }

    @Override
    public void libDirName(String libDirName) {
        this.libDirName = libDirName;
    }

    @Override
    public Property<Boolean> getGenerateDeploymentDescriptor() {
        return generateDeploymentDescriptor;
    }

    @Override
    public DeploymentDescriptor getDeploymentDescriptor() {
        return deploymentDescriptor;
    }

    @Override
    public void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor) {
        this.deploymentDescriptor = deploymentDescriptor;
    }

    @Override
    public DefaultEarPluginConvention deploymentDescriptor(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, forceDeploymentDescriptor());
        return this;
    }

    @Override
    public DefaultEarPluginConvention deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction) {
        configureAction.execute(forceDeploymentDescriptor());
        return this;
    }

    private DeploymentDescriptor forceDeploymentDescriptor() {
        if (deploymentDescriptor == null) {
            deploymentDescriptor = objectFactory.newInstance(DefaultDeploymentDescriptor.class);
        }
        return deploymentDescriptor;
    }
}
