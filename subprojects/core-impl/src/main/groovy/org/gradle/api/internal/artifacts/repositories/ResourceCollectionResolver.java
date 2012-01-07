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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.plugins.conflict.ConflictManager;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.util.MDResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.plugins.resolver.util.ResolverHelper;
import org.apache.ivy.plugins.resolver.util.ResourceMDParser;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.apache.ivy.util.ChecksumHelper;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.repositories.transport.ResourceCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class ResourceCollectionResolver extends BasicResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCollectionResolver.class);

    private List<String> ivyPatterns = new ArrayList<String>();
    private List<String> artifactPatterns = new ArrayList<String>();
    private boolean m2compatible = false;
    private final ResourceCollection repository;

    public ResourceCollectionResolver(String name, ResourceCollection repository) {
        setName(name);
        this.repository = repository;
    }

    protected ResourceCollection getRepository() {
        return repository;
    }

    public ResolvedResource findIvyFileRef(DependencyDescriptor dd, ResolveData data) {
        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        if (isM2compatible()) {
            mrid = convertM2IdForResourceSearch(mrid);
        }
        return findResourceUsingPatterns(mrid, ivyPatterns, DefaultArtifact.newIvyArtifact(mrid,
            data.getDate()), getRMDParser(dd, data), data.getDate());
    }

    protected ResolvedResource findArtifactRef(Artifact artifact, Date date) {
        ModuleRevisionId mrid = artifact.getModuleRevisionId();
        if (isM2compatible()) {
            mrid = convertM2IdForResourceSearch(mrid);
        }
        return findResourceUsingPatterns(mrid, artifactPatterns, artifact,
            getDefaultRMDParser(artifact.getModuleRevisionId().getModuleId()), date);
    }

    protected ResolvedResource findResourceUsingPatterns(ModuleRevisionId moduleRevision,
            List patternList, Artifact artifact, ResourceMDParser rmdparser, Date date) {
        List resolvedResources = new ArrayList();
        Set foundRevisions = new HashSet();
        boolean dynamic = getSettings().getVersionMatcher().isDynamic(moduleRevision);
        boolean stop = false;
        for (Iterator iter = patternList.iterator(); iter.hasNext() && !stop;) {
            String pattern = (String) iter.next();
            ResolvedResource rres = findResourceUsingPattern(
                moduleRevision, pattern, artifact, rmdparser, date);
            if ((rres != null) && !foundRevisions.contains(rres.getRevision())) {
                // only add the first found ResolvedResource for each revision
                foundRevisions.add(rres.getRevision());
                resolvedResources.add(rres);
                stop = !dynamic; // stop iterating if we are not searching a dynamic revision
            }
        }

        if (resolvedResources.size() > 1) {
            ResolvedResource[] rress = (ResolvedResource[]) resolvedResources
                    .toArray(new ResolvedResource[resolvedResources.size()]);
            return findResource(rress, rmdparser, moduleRevision, date);
        } else if (resolvedResources.size() == 1) {
            return (ResolvedResource) resolvedResources.get(0);
        } else {
            return null;
        }
    }

    public ResolvedResource findResource(ResolvedResource[] rress, ResourceMDParser rmdparser,
            ModuleRevisionId mrid, Date date) {
        String name = getName();
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();

        ResolvedResource found = null;
        List sorted = getLatestStrategy().sort(rress);
        List rejected = new ArrayList();
        List foundBlacklisted = new ArrayList();
        IvyContext context = IvyContext.getContext();

        for (ListIterator iter = sorted.listIterator(sorted.size()); iter.hasPrevious();) {
            ResolvedResource rres = (ResolvedResource) iter.previous();
            // we start by filtering based on information already available,
            // even though we don't even know if the resource actually exist.
            // But checking for existence is most of the time more costly than checking
            // name, blacklisting and first level version matching
            if (filterNames(new ArrayList(Collections.singleton(rres.getRevision()))).isEmpty()) {
                Message.debug("\t" + name + ": filtered by name: " + rres);
                continue;
            }
            ModuleRevisionId foundMrid = ModuleRevisionId.newInstance(mrid, rres.getRevision());

            ResolveData data = context.getResolveData();
            if (data != null
                    && data.getReport() != null
                    && data.isBlacklisted(data.getReport().getConfiguration(), foundMrid)) {
                Message.debug("\t" + name + ": blacklisted: " + rres);
                rejected.add(rres.getRevision() + " (blacklisted)");
                foundBlacklisted.add(foundMrid);
                continue;
            }

            if (!versionMatcher.accept(mrid, foundMrid)) {
                Message.debug("\t" + name + ": rejected by version matcher: " + rres);
                rejected.add(rres.getRevision());
                continue;
            }
            if (!rres.getResource().exists()) {
                Message.debug("\t" + name + ": unreachable: " + rres
                    + "; res=" + rres.getResource());
                rejected.add(rres.getRevision() + " (unreachable)");
                continue;
            }
            if ((date != null && rres.getLastModified() > date.getTime())) {
                Message.verbose("\t" + name + ": too young: " + rres);
                rejected.add(rres.getRevision() + " (" + rres.getLastModified() + ")");
                continue;
            }
            if (versionMatcher.needModuleDescriptor(mrid, foundMrid)) {
                ResolvedResource r = rmdparser.parse(rres.getResource(), rres.getRevision());
                if (r == null) {
                    Message.debug("\t" + name
                        + ": impossible to get module descriptor resource: " + rres);
                    rejected.add(rres.getRevision() + " (no or bad MD)");
                    continue;
                }
                ModuleDescriptor md = ((MDResolvedResource) r).getResolvedModuleRevision()
                        .getDescriptor();
                if (md.isDefault()) {
                    Message.debug("\t" + name + ": default md rejected by version matcher"
                            + "requiring module descriptor: " + rres);
                    rejected.add(rres.getRevision() + " (MD)");
                    continue;
                } else if (!versionMatcher.accept(mrid, md)) {
                    Message.debug("\t" + name + ": md rejected by version matcher: " + rres);
                    rejected.add(rres.getRevision() + " (MD)");
                    continue;
                } else {
                    found = r;
                }
            } else {
                found = rres;
            }

            if (found != null) {
                break;
            }
        }
        if (found == null && !rejected.isEmpty()) {
            logAttempt(rejected.toString());
        }
        if (found == null && !foundBlacklisted.isEmpty()) {
            // all acceptable versions have been blacklisted, this means that an unsolvable conflict
            // has been found
            DependencyDescriptor dd = context.getDependencyDescriptor();
            IvyNode parentNode = context.getResolveData().getNode(dd.getParentRevisionId());
            ConflictManager cm = parentNode.getConflictManager(mrid.getModuleId());
            cm.handleAllBlacklistedRevisions(dd, foundBlacklisted);
        }

        return found;
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        EventManager eventManager = getEventManager();
        try {
            if (eventManager != null) {
                repository.addTransferListener(eventManager);
            }
            return super.download(artifacts, options);
        } finally {
            if (eventManager != null) {
                repository.removeTransferListener(eventManager);
            }
        }
    }

    protected ResolvedResource findResourceUsingPattern(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact, ResourceMDParser resourceParser, Date date) {
        String name = getName();
        VersionMatcher versionMatcher = getSettings().getVersionMatcher();
        try {
            if (!versionMatcher.isDynamic(moduleRevisionId)) {
                return findStaticResourceUsingPattern(moduleRevisionId, pattern, artifact);
            } else {
                return findDynamicResourceUsingPattern(resourceParser, moduleRevisionId, pattern, artifact, date);
            }
        } catch (IOException ex) {
            throw new RuntimeException(name + ": unable to get resource for " + moduleRevisionId + ": res="
                    + IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact) + ": " + ex, ex);
        }
    }

    private ResolvedResource findStaticResourceUsingPattern(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) throws IOException {
        String resourceName = IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact);
        logAttempt(resourceName);

        LOGGER.debug("Loading {}", resourceName);
        Resource res = getResource(resourceName, artifact);
        if (res.exists()) {
            String revision = moduleRevisionId.getRevision();
            return new ResolvedResource(res, revision);
        } else {
            LOGGER.debug("Resource not reachable for {}: res={}", moduleRevisionId, res);
            return null;
        }
    }

    private ResolvedResource findDynamicResourceUsingPattern(ResourceMDParser resourceParser, ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact, Date date) {
        logAttempt(IvyPatternHelper.substitute(pattern, ModuleRevisionId.newInstance(moduleRevisionId, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY)), artifact));
        ResolvedResource[] resourceResources = listResources(moduleRevisionId, pattern, artifact);
        if (resourceResources == null) {
            LOGGER.debug("Unable to list resources for {}: pattern={}", moduleRevisionId, pattern);
            return null;
        } else {
            ResolvedResource found = findResource(resourceResources, resourceParser, moduleRevisionId, date);
            if (found == null) {
                LOGGER.debug("No resource found for {}: pattern={}", moduleRevisionId, pattern);
            }
            return found;
        }
    }

    protected Resource getResource(String source) throws IOException {
        return repository.getResource(source);
    }
    
    protected Resource getResource(String source, Artifact target) throws IOException {
        return repository.getResource(source, target.getId());
    }

    /**
     * List all revisions as resolved resources for the given artifact in the given repository using the given pattern, and using the given module id except its revision.
     *
     * @param moduleRevisionId the module revision id to look for (except revision)
     * @param pattern the pattern to use to locate the revisions
     * @param artifact the artifact to find
     * @return an array of ResolvedResource, all pointing to a different revision of the given Artifact.
     */
    protected ResolvedResource[] listResources(ModuleRevisionId moduleRevisionId, String pattern, Artifact artifact) {
        // substitute all but revision
        ModuleRevisionId idWithoutRevision = ModuleRevisionId.newInstance(moduleRevisionId, IvyPatternHelper.getTokenString(IvyPatternHelper.REVISION_KEY));
        String partiallyResolvedPattern = IvyPatternHelper.substitute(pattern, idWithoutRevision, artifact);
        LOGGER.debug("Listing all in {}", partiallyResolvedPattern);

        String[] revisions = ResolverHelper.listTokenValues(repository, partiallyResolvedPattern, IvyPatternHelper.REVISION_KEY);
        if (revisions != null) {
            LOGGER.debug("Found revisions: {}", Arrays.asList(revisions));
            List<ResolvedResource> resources = new ArrayList<ResolvedResource>(revisions.length);
            for (String revision : revisions) {
                String resourcePath = IvyPatternHelper.substituteToken(partiallyResolvedPattern, IvyPatternHelper.REVISION_KEY, revision);
                try {
                    Resource resource = getResource(resourcePath, artifact);
                    if (resource != null) {
                        // we do not test if the resource actually exist here, it would cause
                        // a lot of checks which are not always necessary depending on the usage
                        // which is done of the returned ResolvedResource array
                        resources.add(new ResolvedResource(resource, revision));
                    }
                } catch (IOException e) {
                    LOGGER.warn("Could not get resource listed by repository:" + resourcePath, e);
                }
            }
            if (revisions.length != resources.size()) {
                LOGGER.debug("Found resolved resources: {}", resources);
            }
            return resources.toArray(new ResolvedResource[resources.size()]);
        }
        return null;
    }

    protected long get(Resource resource, File destination) throws IOException {
        LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
        if (destination.getParentFile() != null) {
            destination.getParentFile().mkdirs();
        }
        repository.get(resource.getName(), destination);
        return destination.length();
    }

    public void publish(Artifact artifact, File src, boolean overwrite) throws IOException {
        String destinationPattern;
        if ("ivy".equals(artifact.getType()) && !getIvyPatterns().isEmpty()) {
            destinationPattern = (String) getIvyPatterns().get(0);
        } else if (!getArtifactPatterns().isEmpty()) {
            destinationPattern = (String) getArtifactPatterns().get(0);
        } else {
            throw new IllegalStateException("impossible to publish " + artifact + " using " + this + ": no artifact pattern defined");
        }
        // Check for m2 compatibility
        ModuleRevisionId moduleRevisionId = artifact.getModuleRevisionId();
        if (isM2compatible()) {
            moduleRevisionId = convertM2IdForResourceSearch(moduleRevisionId);
        }

        String destination = getDestination(destinationPattern, artifact, moduleRevisionId);

        put(artifact, src, destination, overwrite);
        LOGGER.info("Published {} to {}", artifact.getName(), hidePassword(destination));
    }

    protected String getDestination(String pattern, Artifact artifact, ModuleRevisionId moduleRevisionId) {
        return IvyPatternHelper.substitute(pattern, moduleRevisionId, artifact);
    }

    protected void put(Artifact artifact, File src, String destination, boolean overwrite) throws IOException {
        // verify the checksum algorithms before uploading artifacts!
        String[] checksums = getChecksumAlgorithms();
        for (String checksum : checksums) {
            if (!ChecksumHelper.isKnownAlgorithm(checksum)) {
                throw new IllegalArgumentException("Unknown checksum algorithm: " + checksum);
            }
        }

        repository.put(artifact, src, destination, overwrite);
        for (String checksum : checksums) {
            putChecksum(artifact, src, destination, overwrite, checksum);
        }
    }

    protected void putChecksum(Artifact artifact, File src, String destination, boolean overwrite,
                               String algorithm) throws IOException {
        File csFile = File.createTempFile("ivytemp", algorithm);
        try {
            FileUtil.copy(new ByteArrayInputStream(ChecksumHelper.computeAsString(src, algorithm)
                    .getBytes()), csFile, null);
            repository.put(DefaultArtifact.cloneWithAnotherTypeAndExt(artifact, algorithm,
                    artifact.getExt() + "." + algorithm), csFile, destination + "." + algorithm, overwrite);
        } finally {
            csFile.delete();
        }
    }

    protected void findTokenValues(Collection names, List patterns, Map tokenValues, String token) {
        for (Object pattern : patterns) {
            String partiallyResolvedPattern = IvyPatternHelper.substituteTokens((String) pattern, tokenValues);
            String[] values = ResolverHelper.listTokenValues(repository, partiallyResolvedPattern, token);
            if (values != null) {
                names.addAll(filterNames(new ArrayList(Arrays.asList(values))));
            }
        }
    }

    protected boolean exist(String path) {
        try {
            Resource resource = repository.getResource(path);
            return resource.exists();
        } catch (IOException e) {
            return false;
        }
    }

    protected Collection findNames(Map tokenValues, String token) {
        throw new UnsupportedOperationException();
    }

    protected Collection filterNames(Collection names) {
        getSettings().filterIgnore(names);
        return names;
    }

    public void addIvyPattern(String pattern) {
        ivyPatterns.add(pattern);
    }

    public void addArtifactPattern(String pattern) {
        artifactPatterns.add(pattern);
    }

    public List getIvyPatterns() {
        return Collections.unmodifiableList(ivyPatterns);
    }

    public List getArtifactPatterns() {
        return Collections.unmodifiableList(artifactPatterns);
    }

    protected void setIvyPatterns(List patterns) {
        ivyPatterns = patterns;
    }

    protected void setArtifactPatterns(List patterns) {
        artifactPatterns = patterns;
    }

    public void dumpSettings() {
        super.dumpSettings();
        Message.debug("\t\tm2compatible: " + isM2compatible());
        Message.debug("\t\tivy patterns:");
        for (ListIterator iter = getIvyPatterns().listIterator(); iter.hasNext();) {
            String p = (String) iter.next();
            Message.debug("\t\t\t" + p);
        }
        Message.debug("\t\tartifact patterns:");
        for (ListIterator iter = getArtifactPatterns().listIterator(); iter.hasNext();) {
            String p = (String) iter.next();
            Message.debug("\t\t\t" + p);
        }
        Message.debug("\t\trepository: " + repository);
    }

    public boolean isM2compatible() {
        return m2compatible;
    }

    public void setM2compatible(boolean compatible) {
        m2compatible = compatible;
    }

    protected ModuleRevisionId convertM2IdForResourceSearch(ModuleRevisionId mrid) {
        if (mrid.getOrganisation() == null || mrid.getOrganisation().indexOf('.') == -1) {
            return mrid;
        }
        return ModuleRevisionId.newInstance(mrid.getOrganisation().replace('.', '/'),
            mrid.getName(), mrid.getBranch(), mrid.getRevision(),
            mrid.getQualifiedExtraAttributes());
    }
}
