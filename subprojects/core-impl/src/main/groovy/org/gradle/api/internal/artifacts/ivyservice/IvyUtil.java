/*
 * Copyright 2009 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.util.GUtil;

import java.util.Map;

import static java.util.Collections.emptyMap;

public class IvyUtil {

    private static final Object MODULE_ID_LOCK = new Object(); //see GRADLE-3027

    public static ModuleRevisionId createModuleRevisionId(Module module) {
        return createModuleRevisionId(module.getGroup(), module.getName(), module.getVersion());
    }

    public static ModuleRevisionId createModuleRevisionId(Dependency dependency) {
        return createModuleRevisionId(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    public static ModuleRevisionId createModuleRevisionId(String group, String name, String version) {
        return createModuleRevisionId(emptyStringIfNull(group), name, null, emptyStringIfNull(version), emptyMap());
    }

    public static ModuleRevisionId createModuleRevisionId(ModuleVersionIdentifier id) {
        return createModuleRevisionId(id.getGroup(), id.getName(), id.getVersion());
    }

    public static ModuleRevisionId createModuleRevisionId(ModuleComponentIdentifier id) {
        return createModuleRevisionId(id.getGroup(), id.getModule(), id.getVersion());
    }

    public static ModuleRevisionId createModuleRevisionId(ModuleRevisionId revId, String version) {
        return createModuleRevisionId(revId.getOrganisation(), revId.getName(), revId.getBranch(), version, revId.getQualifiedExtraAttributes());
    }

    private static String emptyStringIfNull(String value) {
        return GUtil.elvis(value, "");
    }

    public static ModuleRevisionId createModuleRevisionId(String org, String name, String branch, String rev, Map extraAttributes) {
        return createModuleRevisionId(org, name, branch, rev, extraAttributes, true);
    }

    public static ModuleRevisionId createModuleRevisionId(String org, String name, String branch, String revConstraint, Map extraAttributes, boolean replaceNullBranchWithDefault) {
        synchronized (MODULE_ID_LOCK) {
            return ModuleRevisionId.newInstance(org, name, branch, revConstraint, extraAttributes, replaceNullBranchWithDefault);
        }
    }

    public static ModuleId createModuleId(String org, String name) {
        synchronized (MODULE_ID_LOCK) {
            return ModuleId.newInstance(org, name);
        }
    }

    public static DefaultModuleDescriptor createModuleDescriptor(DependencyDescriptor dependencyDescriptor) {
        DefaultModuleDescriptor moduleDescriptor = DefaultModuleDescriptor.newDefaultInstance(dependencyDescriptor.getDependencyRevisionId(), dependencyDescriptor.getAllDependencyArtifacts());
        moduleDescriptor.setStatus("integration");
        return moduleDescriptor;
    }
}