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

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.text.ParseException;

/**
 * Entry point to the resolvers. The delegate contains all the other resolvers.
 * <p>
 * by Szczepan Faber, created at: 10/7/11
 */
public class EntryPointResolver extends AbstractLimitedDependencyResolver implements DependencyResolver {

    private final DependencyResolver delegate;
    private IvyResolutionListener ivyResolutionListener;

    public EntryPointResolver(DependencyResolver delegate) {
        assert delegate != null : "Delegate cannot be null!";
        this.delegate = delegate;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        assert ivyResolutionListener != null : "ivyResolutionListener was not configured";
        ivyResolutionListener.beforeMetadataResolved(dd, data);
        return delegate.getDependency(dd, data);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return delegate.download(artifacts, options);
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return delegate.locate(artifact);
    }

    public void setIvyResolutionListener(IvyResolutionListener ivyResolutionListener) {
        this.ivyResolutionListener = ivyResolutionListener;
    }
}
