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
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor;

import javax.inject.Inject;

import static org.gradle.api.reflect.TypeOf.typeOf;

@SuppressWarnings("deprecation")
public class DefaultEarPluginConvention extends org.gradle.plugins.ear.EarPluginConvention implements HasPublicType {

    private final DefaultEarPluginExtension extension;

    @Inject
    public DefaultEarPluginConvention(DefaultEarPluginExtension extension) {
        this.extension = extension;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.plugins.ear.EarPluginConvention.class);
    }

    @Override
    public String getAppDirName() {
        return extension.getAppDirName();
    }

    @Override
    public void setAppDirName(String appDirName) {
        extension.setAppDirName(appDirName);
    }

    @Override
    public void appDirName(String appDirName) {
        this.setAppDirName(appDirName);
    }

    @Override
    public String getLibDirName() {
        return extension.getLibDirName();
    }

    @Override
    public void setLibDirName(String libDirName) {
        extension.setLibDirName(libDirName);
    }

    @Override
    public void libDirName(String libDirName) {
        extension.libDirName(libDirName);
    }

    @Override
    public Property<Boolean> getGenerateDeploymentDescriptor() {
        return extension.getGenerateDeploymentDescriptor();
    }

    @Override
    public DeploymentDescriptor getDeploymentDescriptor() {
        return extension.getDeploymentDescriptor();
    }

    @Override
    public void setDeploymentDescriptor(DeploymentDescriptor deploymentDescriptor) {
        extension.setDeploymentDescriptor(deploymentDescriptor);
    }

    @Override
    public DefaultEarPluginConvention deploymentDescriptor(Closure configureClosure) {
        extension.deploymentDescriptor(configureClosure);
        return this;
    }

    @Override
    public DefaultEarPluginConvention deploymentDescriptor(Action<? super DeploymentDescriptor> configureAction) {
        extension.deploymentDescriptor(configureAction);
        return this;
    }
}
