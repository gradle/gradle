/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService;
import org.gradle.internal.enterprise.GradleEnterprisePluginBackgroundJobExecutors;
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class DefaultGradleEnterprisePluginRequiredServices implements GradleEnterprisePluginRequiredServices {

    private final UserInputHandler userInputHandler;
    private final StyledTextOutputFactory styledTextOutputFactory;
    private final GradleEnterprisePluginBackgroundJobExecutors backgroundJobExecutors;
    private final DevelocityPluginUnsafeConfigurationService unsafeConfigurationService;

    public DefaultGradleEnterprisePluginRequiredServices(
        UserInputHandler userInputHandler,
        StyledTextOutputFactory styledTextOutputFactory,
        GradleEnterprisePluginBackgroundJobExecutors backgroundJobExecutors,
        DevelocityPluginUnsafeConfigurationService unsafeConfigurationService
    ) {
        this.userInputHandler = userInputHandler;
        this.styledTextOutputFactory = styledTextOutputFactory;
        this.backgroundJobExecutors = backgroundJobExecutors;
        this.unsafeConfigurationService = unsafeConfigurationService;
    }

    @Override
    public UserInputHandler getUserInputHandler() {
        return userInputHandler;
    }

    @Override
    public StyledTextOutputFactory getStyledTextOutputFactory() {
        return styledTextOutputFactory;
    }

    @Override
    public GradleEnterprisePluginBackgroundJobExecutors getBackgroundJobExecutors() {
        return backgroundJobExecutors;
    }

    @Override
    public DevelocityPluginUnsafeConfigurationService getUnsafeConfigurationService() {
        return unsafeConfigurationService;
    }
}
