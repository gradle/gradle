/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;

public class NotifyingSettingsProcessor implements SettingsProcessor {
    private final SettingsProcessor settingsProcessor;
    private final BuildOperationExecutor buildOperationExecutor;

    public NotifyingSettingsProcessor(SettingsProcessor settingsProcessor, BuildOperationExecutor buildOperationExecutor) {
        this.settingsProcessor = settingsProcessor;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public SettingsInternal process(final GradleInternal gradle, final SettingsLocation settingsLocation, final ClassLoaderScope buildRootClassLoaderScope, final StartParameter startParameter) {
        BuildOperationDetails operationDetails = BuildOperationDetails.displayName("Configure settings").progressDisplayName("settings").build();
        return buildOperationExecutor.run(operationDetails, new Transformer<SettingsInternal, BuildOperationContext>() {
            @Override
            public SettingsInternal transform(BuildOperationContext buildOperationContext) {
                return settingsProcessor.process(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
            }
        });
    }
}
