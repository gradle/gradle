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

package org.gradle.tooling.events.configuration.internal;

import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult;
import org.gradle.tooling.events.internal.DefaultFinishEvent;

public class DefaultProjectConfigurationFinishEvent extends DefaultFinishEvent implements ProjectConfigurationFinishEvent {

    public DefaultProjectConfigurationFinishEvent(long eventTime, String displayName, ProjectConfigurationOperationDescriptor descriptor, ProjectConfigurationOperationResult result) {
        super(eventTime, displayName, descriptor, result);
    }

    @Override
    public ProjectConfigurationOperationDescriptor getDescriptor() {
        return (ProjectConfigurationOperationDescriptor) super.getDescriptor();
    }

}
