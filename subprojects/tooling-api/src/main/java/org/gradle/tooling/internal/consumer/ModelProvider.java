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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.internal.build.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.converters.GradleProjectConverter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.internal.Exceptions;

/**
 * by Szczepan Faber, created at: 12/21/11
 */
public class ModelProvider {
    public <T> T provide(ConsumerConnection connection, Class<T> modelType, ConsumerOperationParameters operationParameters) {
        VersionDetails version = connection.getVersionDetails();

        Class<?> protocolType = version.mapModelTypeToProtocolType(modelType);
        if (protocolType == null) {
            throw new UnknownModelException(
                    "Unknown model: '" + modelType.getSimpleName() + "'.\n"
                        + "Most likely you are trying to acquire a model for a class that is not a valid Tooling API model class.\n"
                        + "Review the documentation on the version of Tooling API you use to find out what models can be build."
            );
        }

        if (operationParameters.getJavaHome() != null) {
            if(!version.supportsConfiguringJavaHome()) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJavaHome() and buildLauncher.setJavaHome()");
            }
        }
        if (operationParameters.getJvmArguments() != null) {
            if (!version.supportsConfiguringJvmArguments()) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJvmArguments() and buildLauncher.setJvmArguments()");
            }
        }
        if (operationParameters.getStandardInput() != null) {
            if (!version.supportsConfiguringStandardInput()) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setStandardInput() and buildLauncher.setStandardInput()");
            }
        }
        if (modelType != Void.class && operationParameters.getTasks() != null) {
            if (!version.supportsRunningTasksWhenBuildingModel()) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.forTasks()");
            }
        }

        if (modelType == BuildEnvironment.class && !version.isModelSupported(BuildEnvironment.class)) {
            //early versions of provider do not support BuildEnvironment model
            //since we know the gradle version at least we can give back some result
            VersionOnlyBuildEnvironment out = new VersionOnlyBuildEnvironment(version.getVersion());
            return modelType.cast(out);
        }
        if (modelType == GradleProject.class && !version.isModelSupported(GradleProject.class)) {
            //we broke compatibility around M9 wrt getting the tasks of a project (issue GRADLE-1875)
            //this patch enables getting gradle tasks for target gradle version pre M5
            EclipseProjectVersion3 project = connection.run(EclipseProjectVersion3.class, operationParameters);
            GradleProject gradleProject = new GradleProjectConverter().convert(project);
            return modelType.cast(gradleProject);
        }
        if (!version.isModelSupported(modelType)) {
            //don't bother asking the provider for this model
            throw new UnsupportedOperationException(String.format("I don't know how to build a model of type '%s'.", modelType.getSimpleName()));
        }

        return connection.run(modelType, operationParameters);
    }
}
