/*
 * Copyright 2007-2008 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class IvyUtil {
    public static List<String> getAllMasterConfs(Configuration[] allMasterConfigurationObjects) {
        List<String> masterConfigurationNames = new ArrayList<String>();
        for (Configuration masterConfigurationObject : allMasterConfigurationObjects) {
            masterConfigurationNames.add(masterConfigurationObject.getName());
        }
        return masterConfigurationNames;
    }

    public static ModuleRevisionId createModuleRevisionId(Module module) {
        return new ModuleRevisionId(new ModuleId(module.getGroup(), module.getName()), module.getVersion().toString());
    }
}
