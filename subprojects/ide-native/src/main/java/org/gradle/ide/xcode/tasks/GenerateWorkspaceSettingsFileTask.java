/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.tasks;

import org.gradle.api.Incubating;
import org.gradle.ide.xcode.tasks.internal.XcodeWorkspaceSettingsFile;
import org.gradle.plugins.ide.api.PropertyListGeneratorTask;

/**
 * Task for generating a workspace settings file.
 *
 * @since 4.2
 */
@Incubating
public class GenerateWorkspaceSettingsFileTask extends PropertyListGeneratorTask<XcodeWorkspaceSettingsFile> {
    @Override
    protected void configure(XcodeWorkspaceSettingsFile settingsFile) {
    }

    @Override
    protected XcodeWorkspaceSettingsFile create() {
        return new XcodeWorkspaceSettingsFile(getPropertyListTransformer());
    }
}
