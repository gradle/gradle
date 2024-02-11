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

package org.gradle.ide.xcode.internal;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.ide.xcode.XcodeWorkspace;

import javax.inject.Inject;

public class DefaultXcodeWorkspace implements XcodeWorkspace {
    private final DirectoryProperty workspaceDir;

    @Inject
    public DefaultXcodeWorkspace(ObjectFactory objectFactory) {
        workspaceDir = objectFactory.directoryProperty();
    }

    @Override
    public DirectoryProperty getLocation() {
        return workspaceDir;
    }

    @Override
    public String getDisplayName() {
        return "Xcode workspace";
    }
}
