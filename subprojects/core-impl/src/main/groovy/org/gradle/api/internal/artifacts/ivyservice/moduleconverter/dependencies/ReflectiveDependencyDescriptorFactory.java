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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Ivy's dependency descriptor that is a copy of provided source descriptor with modified org, name and version.
 */
public class ReflectiveDependencyDescriptorFactory {

    public DependencyDescriptor create(DependencyDescriptor source, ModuleRevisionId targetId) {
        if (!(source instanceof DefaultDependencyDescriptor)) {
            throw new IllegalArgumentException("I can only create descriptors out of DefaultDependencyDescriptor");
        }
        DefaultDependencyDescriptor out = new DefaultDependencyDescriptor(moduleDescriptor(source), targetId, source.getDynamicConstraintDependencyRevisionId(), source.isForce(), source.isChanging(), source.isTransitive());

        setProperty(out, "parentId", getProperty(source, "parentId"));
        setProperty(out, "namespace", source.getNamespace());
        ((Map) getProperty(out, "confs")).putAll((Map) getProperty(source, "confs"));

        Map sourceExcludeRules = (Map) getProperty(source, "excludeRules");
        setProperty(out, "excludeRules", sourceExcludeRules == null? null: new LinkedHashMap(sourceExcludeRules));

        Map sourceIncludeRules = (Map) getProperty(source, "includeRules");
        setProperty(out, "includeRules", sourceIncludeRules == null ? null : new LinkedHashMap(sourceIncludeRules));

        Map dependencyArtifacts = (Map) getProperty(source, "dependencyArtifacts");
        setProperty(out, "dependencyArtifacts", dependencyArtifacts == null? null: new LinkedHashMap(dependencyArtifacts));

        setProperty(out, "sourceModule", source.getSourceModule());

        return out;
    }

    private static ModuleDescriptor moduleDescriptor(DependencyDescriptor source) {
        return (ModuleDescriptor) getProperty(source, "md");
    }

    private static Object getProperty(Object object, String property) {
        try {
            Field field = DefaultDependencyDescriptor.class.getDeclaredField(property);
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception e) {
            throw throwAsUncheckedException(e);
        }
    }

    private static void setProperty(Object object, String property, Object value) {
        try {
            Field field = DefaultDependencyDescriptor.class.getDeclaredField(property);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            throw throwAsUncheckedException(e);
        }
    }
}
