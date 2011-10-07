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
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.util.extendable.UnmodifiableExtendableItem;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolutionStrategy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.Map;

/**
 * Entry point to the resolvers. The delegate contains all the other resolvers. <p> by Szczepan Faber, created at: 10/7/11
 */
public class EntryPointResolver extends AbstractResolver {

    private final DependencyResolver delegate;
    private DependencySet forcedVersions;

    public EntryPointResolver(DependencyResolver delegate) {
        assert delegate != null : "Delegate cannot be null!";
        this.delegate = delegate;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        //TODO SF - this resolver needs to be more generic, for now doing the work here...
        if (forcedVersions != null) {
            for (Dependency forced : forcedVersions) {
                if (ModuleId.newInstance(forced.getGroup(), forced.getName()).equals(dd.getDependencyId())) {
//                    ModuleRevisionId newRevId = ModuleRevisionId.newInstance(forced.getGroup(), forced.getName(), forced.getVersion());
//                    DependencyDescriptor forcedVersion = dd.clone(newRevId);
                    updateFieldValue(dd.getDependencyRevisionId(), "revision", forced.getVersion());
                    ((Map) getFieldValue(UnmodifiableExtendableItem.class, dd.getDependencyRevisionId(), "attributes"))
                            .put(IvyPatternHelper.REVISION_KEY, forced.getVersion());
                    ResolvedModuleRevision ret = delegate.getDependency(dd, data);
                    return ret;
                }
            }
        }

        ResolvedModuleRevision out = delegate.getDependency(dd, data);
        return out;
    }

    private Object getFieldValue(Class targetClass, Object target, String fieldName) {
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Unable to perform reflection hacks on ivy object.", e);
        }
    }

    private void updateFieldValue(Object target, String fieldName, String fieldValue) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, fieldValue);
        } catch (Exception e) {
            throw new RuntimeException("Unable to perform reflection hacks on ivy object.", e);
        }
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return delegate.download(artifacts, options);
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        delegate.publish(artifact, src, overwrite);
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        return delegate.findIvyFileRef(dd, data);
    }

    public void configureUsing(ResolutionStrategy resolutionStrategy) {
        this.forcedVersions = resolutionStrategy.getForcedVersions();
    }
}
