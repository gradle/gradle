/*
 * Copyright 2011 the original author or authors.
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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.util.extendable.UnmodifiableExtendableItem;
import org.gradle.api.artifacts.ForcedVersion;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Using reflections magic we update the ivy objects to force certain version of dependencies.
 * <p>
 * by Szczepan Faber, created at: 10/14/11
 */
class MaybeForceVersions implements IvyResolutionListener {
    private final Set<ForcedVersion> forcedVersions;

    public MaybeForceVersions(Set<ForcedVersion> forcedVersions) {
        this.forcedVersions = forcedVersions;
    }

    public void beforeMetadataResolved(DependencyDescriptor dd, ResolveData data) {
        if (forcedVersions == null) {
            return;
        }
        for (ForcedVersion forced : forcedVersions) {
            if (ModuleId.newInstance(forced.getGroup(), forced.getName()).equals(dd.getDependencyId())) {
                updateFieldValue(dd.getDependencyRevisionId(), "revision", forced.getVersion());
                ((Map) getFieldValue(UnmodifiableExtendableItem.class, dd.getDependencyRevisionId(), "attributes"))
                        .put(IvyPatternHelper.REVISION_KEY, forced.getVersion());
                break;
            }
        }
    }

    private Object getFieldValue(Class targetClass, Object target, String fieldName) {
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Unable to reflectively get value from the ivy object: field: " + fieldName
                    + ", class: " + targetClass.getSimpleName() + ", target: " + target, e);
        }
    }

    private void updateFieldValue(Object target, String fieldName, String fieldValue) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, fieldValue);
        } catch (Exception e) {
            throw new RuntimeException("Unable to reflectively update the ivy object: field: " + fieldName
                    + ", value: " + fieldValue + ", target: " + target, e);
        }
    }
}
