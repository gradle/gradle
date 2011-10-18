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

import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.DynamicRevisionExpiryPolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions.DynamicRevisionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserResolverChain extends ChainResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);
    
    private final Map<ModuleRevisionId, DependencyResolver> artifactResolvers = new HashMap<ModuleRevisionId, DependencyResolver>();
    private final DynamicRevisionDependencyConverter dynamicRevisions;

    public UserResolverChain(DynamicRevisionCache dynamicRevisionCache) {
        dynamicRevisions = new DynamicRevisionDependencyConverter(dynamicRevisionCache);
    }

    public void setDynamicRevisionExpiryPolicy(DynamicRevisionExpiryPolicy dynamicRevisionExpiryPolicy) {
        dynamicRevisions.setDynamicRevisionExpiryPolicy(dynamicRevisionExpiryPolicy);
    }

    @Override
    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data)
            throws ParseException {

        // First attempt to locate the module in a resolver cache
        for (DependencyResolver resolver : getResolvers()) {
            DependencyDescriptor resolvedDynamicDependency = dynamicRevisions.maybeResolveDynamicRevision(resolver, dd);

            ResolvedModuleRevision cachedModule = findModuleInCache(resolver, resolvedDynamicDependency, data);
            if (cachedModule != null) {
                LOGGER.debug("Found module {} in resolver cache {}", cachedModule, resolver.getName());
                artifactResolvers.put(cachedModule.getId(), resolver);
                return cachedModule;
            }
        }

        // Otherwise delegate to each resolver in turn
        ResolvedModuleRevision downloadedModule = getModuleRevisionFromAnyRepository(dd, data);
        if (downloadedModule != null) {
            LOGGER.debug("Found module {} using resolver {}", downloadedModule, downloadedModule.getArtifactResolver());
            artifactResolvers.put(downloadedModule.getId(), downloadedModule.getArtifactResolver());
            dynamicRevisions.maybeSaveDynamicRevision(dd, downloadedModule);
        }
        return downloadedModule;
    }


    private ResolvedModuleRevision findModuleInCache(DependencyResolver resolver, DependencyDescriptor dd, ResolveData resolveData) {
        CacheMetadataOptions cacheOptions = getCacheMetadataOptions(resolver, resolveData);
        return resolver.getRepositoryCacheManager().findModuleInCache(dd, dd.getDependencyRevisionId(), cacheOptions, resolver.getName());
    }

    private CacheMetadataOptions getCacheMetadataOptions(DependencyResolver resolver, ResolveData resolveData) {
        if (resolver instanceof AbstractResolver) {
            try {
                Method method = AbstractResolver.class.getDeclaredMethod("getCacheOptions", ResolveData.class);
                method.setAccessible(true);
                return (CacheMetadataOptions) method.invoke(resolver, resolveData);
            } catch (Exception e) {
                throw new GradleException("Could not get cache options from AbstractResolver", e);
            }
        }
        return new CacheMetadataOptions();
    }

    private ResolvedModuleRevision getModuleRevisionFromAnyRepository(DependencyDescriptor dd, ResolveData originalData)
            throws ParseException {

        List<Exception> errors = new ArrayList<Exception>();
        ResolvedModuleRevision mr = null;

        ResolveData data = new ResolveData(originalData, originalData.isValidate());
        for (DependencyResolver resolver : getResolvers()) {
            try {
                mr = resolver.getDependency(dd, data);
                data.setCurrentResolvedModuleRevision(mr);
            } catch (Exception ex) {
                Message.verbose("problem occurred while resolving " + dd + " with " + resolver
                        + ": " + StringUtils.getStackTrace(ex));
                errors.add(ex);
            }
        }
        if (mr == null && !errors.isEmpty()) {
            throwResolutionFailure(dd, errors);
        }
        return mr;
    }

    private void throwResolutionFailure(DependencyDescriptor dd, List<Exception> errors) throws ParseException {
        if (errors.size() == 1) {
            Exception ex = errors.get(0);
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else if (ex instanceof ParseException) {
                throw (ParseException) ex;
            } else {
                throw new RuntimeException(ex.toString(), ex);
            }
        } else {
            StringBuilder err = new StringBuilder();
            for (Exception ex : errors) {
                err.append("\t").append(StringUtils.getErrorMessage(ex)).append("\n");
            }
            err.setLength(err.length() - 1);
            throw new RuntimeException("several problems occurred while resolving " + dd + ":\n"
                    + err);
        }
    }

    @Override
    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        DownloadReport overallReport = new DownloadReport();
        for (Artifact artifact : artifacts) {
            DependencyResolver artifactResolver = artifactResolvers.get(artifact.getModuleRevisionId());
            ArtifactDownloadReport artifactDownloadReport;
            if (artifactResolver != null && artifactResolver != this) {
                artifactDownloadReport = downloadFromSingleRepository(artifactResolver, artifact, options);
            } else {
                artifactDownloadReport = downloadFromAnyRepository(artifact, options);
            }
            overallReport.addArtifactReport(artifactDownloadReport);
        }
        return overallReport;
     }

    private ArtifactDownloadReport downloadFromSingleRepository(DependencyResolver artifactResolver, Artifact artifact, DownloadOptions options) {
        LOGGER.debug("Attempting to download artifact {} using resolver {}", artifact, artifactResolver);
        DownloadReport downloadReport = artifactResolver.download(new Artifact[]{artifact}, options);
        return downloadReport.getArtifactReport(artifact);
    }

    private ArtifactDownloadReport downloadFromAnyRepository(Artifact artifact, DownloadOptions options) {
        // Check all of the resolvers in turn, stopping at the first successful match
        Artifact[] singleArtifact = {artifact};
        // TODO Try all repositories for cached artifact first
        LOGGER.debug("Attempting to download {} using all resolvers", artifact);
        for (DependencyResolver resolver : getResolvers()) {
            DownloadReport downloadReport = resolver.download(singleArtifact, options);
            ArtifactDownloadReport artifactDownload = downloadReport.getArtifactReport(artifact);
            if (artifactDownload.getDownloadStatus() != DownloadStatus.FAILED) {
                return artifactDownload;
            }
        }

        ArtifactDownloadReport failedDownload = new ArtifactDownloadReport(artifact);
        failedDownload.setDownloadStatus(DownloadStatus.FAILED);
        return failedDownload;
    }

    @Override
    public List<DependencyResolver> getResolvers() {
        return super.getResolvers();
    }

    private static class DynamicRevisionDependencyConverter {
        private final DynamicRevisionCache dynamicRevisionCache;
        private DynamicRevisionExpiryPolicy dynamicRevisionExpiryPolicy;

        private DynamicRevisionDependencyConverter(DynamicRevisionCache dynamicRevisionCache) {
            this.dynamicRevisionCache = dynamicRevisionCache;
        }

        public void setDynamicRevisionExpiryPolicy(DynamicRevisionExpiryPolicy dynamicRevisionExpiryPolicy) {
            this.dynamicRevisionExpiryPolicy = dynamicRevisionExpiryPolicy;
        }

        public void maybeSaveDynamicRevision(DependencyDescriptor original, ResolvedModuleRevision downloadedModule) {
            ModuleRevisionId originalId = original.getDependencyRevisionId();
            ModuleRevisionId resolvedId = downloadedModule.getId();
            if (originalId.equals(resolvedId)) {
                return;
            }
            dynamicRevisionCache.saveResolvedRevision(downloadedModule.getResolver(), originalId, resolvedId);
        }

        public DependencyDescriptor maybeResolveDynamicRevision(DependencyResolver resolver, DependencyDescriptor original) {
            assert dynamicRevisionExpiryPolicy != null : "dynamicRevisionExpiryPolicy was not configured";

            DynamicRevisionCache.CachedRevision resolvedRevision = dynamicRevisionCache.getResolvedRevision(resolver, original.getDependencyRevisionId());
            if (resolvedRevision == null) {
                return original;
            }
            if (dynamicRevisionExpiryPolicy.isExpired(resolvedRevision.getRevision(), resolvedRevision.getAgeMillis())) {
                return original;
            }
            return original.clone(resolvedRevision.getRevision());
        }

    }
}
