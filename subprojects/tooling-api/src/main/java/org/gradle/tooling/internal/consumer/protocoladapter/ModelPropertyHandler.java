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

package org.gradle.tooling.internal.consumer.protocoladapter;

import org.gradle.tooling.internal.consumer.converters.GradleProjectConverter;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;

import java.lang.reflect.Method;

/**
 * by Szczepan Faber, created at: 4/2/12
 */
public class ModelPropertyHandler {

    private final VersionDetails versionDetails;

    public ModelPropertyHandler(VersionDetails versionDetails) {
        this.versionDetails = versionDetails;
    }

    /**
     * @param method getter for the property
     * @param delegate object that contain the property
     * @return whether this handler should provide the return value for given property.
     */
    public boolean shouldHandle(Method method, Object delegate) {
        return method.getName().equals("getGradleProject")
                && delegate instanceof EclipseProjectVersion3
                && !versionDetails.supportsGradleProjectModel();
    }

    public DefaultGradleProject getPropertyValue(Method method, Object delegate) {
        return new GradleProjectConverter().convert((EclipseProjectVersion3) delegate);
    }
}
