/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ivy.IvyExtraInfo;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;

import java.util.Map;

public class DefaultIvyModuleDescriptor implements IvyModuleDescriptor {
    private final String branch;
    private final String ivyStatus;
    private final IvyExtraInfo extraInfo;

    public DefaultIvyModuleDescriptor(Map<NamespaceId, String> extraInfo, String branch, String ivyStatus) {
        this.extraInfo = new DefaultIvyExtraInfo(extraInfo);
        this.branch = branch;
        this.ivyStatus = ivyStatus;
    }

    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public String getIvyStatus() {
        return ivyStatus;
    }

    @Override
    public IvyExtraInfo getExtraInfo() {
        return extraInfo;
    }
}
