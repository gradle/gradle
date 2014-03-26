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
package org.gradle.api.internal.artifacts.repositories.resolver;

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.MavenClassifierArtifactScheme;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.resolution.ComponentMetaDataArtifact;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.resource.ResourceNotFoundException;
import org.gradle.api.resources.ResourceException;
import org.gradle.util.CollectionUtils;
import org.gradle.util.DeprecationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class MavenResolver extends ExternalResourceResolver implements PatternBasedResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenResolver.class);
    private final RepositoryTransport transport;
    private final String root;
    private final List<String> artifactRoots = new ArrayList<String>();
    private String pattern = MavenPattern.M2_PATTERN;
    private boolean usepoms = true;
    private boolean useMavenMetadata = true;
    private final MavenMetadataLoader mavenMetaDataLoader;

    public MavenResolver(String name, URI rootUri, RepositoryTransport transport,
                         LocallyAvailableResourceFinder<ArtifactIdentifier> locallyAvailableResourceFinder,
                         ResolverStrategy resolverStrategy) {
        super(name, transport.getRepository(),
                new ChainedVersionLister(new MavenVersionLister(transport.getRepository()), new ResourceVersionLister(transport.getRepository())),
                locallyAvailableResourceFinder, new GradlePomModuleDescriptorParser(), resolverStrategy);
        transport.configureCacheManager(this);

        this.mavenMetaDataLoader = new MavenMetadataLoader(transport.getRepository());
        this.transport = transport;
        this.root = transport.convertToPath(rootUri);

        super.setM2compatible(true);

        // SNAPSHOT revisions are changing revisions
        setChangingMatcher(PatternMatcher.REGEXP);
        setChangingPattern(".*-SNAPSHOT");

        updatePatterns();
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        if (isSnapshotVersion(dependency)) {
            final TimestampedModuleSource uniqueSnapshotVersion = findUniqueSnapshotVersion(dependency.getDescriptor().getDependencyRevisionId());
            if (uniqueSnapshotVersion != null) {
                resolveUniqueSnapshotDependency(dependency, result, uniqueSnapshotVersion);
                return;
            }
        }

        resolveStaticDependency(dependency, result, createArtifactResolver());
    }

    private void resolveUniqueSnapshotDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result, TimestampedModuleSource snapshotSource) {
        resolveStaticDependency(dependency, result, createArtifactResolver(snapshotSource));
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            result.setModuleSource(snapshotSource);
        }
    }

    private boolean isSnapshotVersion(DependencyMetaData dd) {
        return dd.getRequested().getVersion().endsWith("SNAPSHOT");
    }

    protected File download(ModuleVersionArtifactMetaData artifact, ModuleSource moduleSource) throws IOException {
        if (moduleSource instanceof TimestampedModuleSource) {
            return downloadArtifact(artifact, createArtifactResolver((TimestampedModuleSource) moduleSource));
        } else {
            return downloadArtifact(artifact, createArtifactResolver());
        }
    }

    private ArtifactResolver createArtifactResolver(TimestampedModuleSource timestampedModuleSource) {
        final String timestampedVersion = timestampedModuleSource.getTimestampedVersion();
        Transformer<String, String> patternTransformer = new Transformer<String, String>() {
            public String transform(String original) {
                return original.replaceFirst("\\-\\[revision\\]", "-" + timestampedVersion);
            }
        };
        return new ArtifactResolver(
                CollectionUtils.collect(getIvyPatterns(), patternTransformer),
                CollectionUtils.collect(getArtifactPatterns(), patternTransformer));
    }

    public void addArtifactLocation(URI baseUri, String pattern) {
        if (pattern != null && pattern.length() > 0) {
            throw new IllegalArgumentException("Maven Resolver only supports a single pattern. It cannot be provided on a per-location basis.");
        }
        artifactRoots.add(transport.convertToPath(baseUri));

        updatePatterns();
    }

    public void addDescriptorLocation(URI baseUri, String pattern) {
        throw new UnsupportedOperationException("Cannot have multiple descriptor urls for MavenResolver");
    }

    private String getWholePattern() {
        return root + pattern;
    }

    private void updatePatterns() {
        if (isUsepoms()) {
            setIvyPatterns(Collections.singletonList(getWholePattern()));
        } else {
            setIvyPatterns(Collections.<String>emptyList());
        }

        List<String> artifactPatterns = new ArrayList<String>();
        artifactPatterns.add(getWholePattern());
        for (String artifactRoot : artifactRoots) {
            artifactPatterns.add(artifactRoot + pattern);
        }
        setArtifactPatterns(artifactPatterns);
    }

    @Override
    protected ModuleVersionArtifactMetaData getMetaDataArtifactFor(ModuleVersionIdentifier moduleVersionIdentifier) {
        if (isUsepoms()) {
            DefaultModuleVersionArtifactIdentifier artifactId =
                    new DefaultModuleVersionArtifactIdentifier(moduleVersionIdentifier, moduleVersionIdentifier.getName(), "pom", "pom", Collections.<String, String>emptyMap());
            return new DefaultModuleVersionArtifactMetaData(artifactId);
        }

        return null;
    }

    private TimestampedModuleSource findUniqueSnapshotVersion(ModuleRevisionId moduleRevisionId) {
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
        DefaultArtifactIdentifier pomArtifact = new DefaultArtifactIdentifier(moduleVersionIdentifier, moduleVersionIdentifier.getName(), "pom", "pom", null);
        String metadataLocation = toResourcePattern(getWholePattern()).toModuleVersionPath(pomArtifact) + "/maven-metadata.xml";
        MavenMetadata mavenMetadata = parseMavenMetadata(metadataLocation);

        if (mavenMetadata.timestamp != null) {
            // we have found a timestamp, so this is a snapshot unique version
            String rev = moduleRevisionId.getRevision();
            rev = rev.substring(0, rev.length() - "SNAPSHOT".length());
            rev = rev + mavenMetadata.timestamp + "-" + mavenMetadata.buildNumber;
            return new TimestampedModuleSource(rev);
        }
        return null;
    }

    private MavenMetadata parseMavenMetadata(String metadataLocation) {
        if (shouldUseMavenMetadata(pattern)) {
            try {
                return mavenMetaDataLoader.load(metadataLocation);
            } catch (ResourceNotFoundException e) {
                return new MavenMetadata();
            } catch (ResourceException e) {
                LOGGER.warn("impossible to access Maven metadata file, ignored.", e);
            }
        }
        return new MavenMetadata();
    }

    // A bunch of configuration properties that we don't (yet) support in our model via the DSL. Users can still tweak these on the resolver using mavenRepo().
    public boolean isUsepoms() {
        return usepoms;
    }

    public void setUsepoms(boolean usepoms) {
        this.usepoms = usepoms;
        updatePatterns();
    }

    public boolean isUseMavenMetadata() {
        return useMavenMetadata;
    }

    @Deprecated
    public void setUseMavenMetadata(boolean useMavenMetadata) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("MavenResolver.setUseMavenMetadata(boolean)");
        this.useMavenMetadata = useMavenMetadata;
        if (useMavenMetadata) {
            this.versionLister = new ChainedVersionLister(
                    new MavenVersionLister(getRepository()),
                    new ResourceVersionLister(getRepository()));
        } else {
            this.versionLister = new ResourceVersionLister(getRepository());
        }
    }

    private boolean shouldUseMavenMetadata(String pattern) {
        return isUseMavenMetadata() && pattern.endsWith(MavenPattern.M2_PATTERN);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern must not be null");
        }
        this.pattern = pattern;
        updatePatterns();
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        throw new UnsupportedOperationException("Cannot configure root on mavenRepo. Use 'url' property instead.");
    }

    @Override
    public void setM2compatible(boolean compatible) {
        if (!compatible) {
            throw new IllegalArgumentException("Cannot set m2compatible = false on mavenRepo.");
        }
    }

    public Set<? extends ComponentArtifactMetaData> getTypedArtifacts(ModuleVersionMetaData module, Class<? extends SoftwareArtifact> artifactType) {

        if (artifactType == ComponentMetaDataArtifact.class) {
            Artifact pomArtifact = DefaultArtifact.newPomArtifact(IvyUtil.createModuleRevisionId(module.getId()), new Date());
            return ImmutableSet.<ComponentArtifactMetaData>of(module.artifact(pomArtifact));
        }

        return new MavenClassifierArtifactScheme().get(module, artifactType);
    }

    @Override
    protected Set<? extends ComponentArtifactMetaData> getOptionalMainArtifacts(ModuleVersionMetaData module) {
        if (module.isMetaDataOnly()) {
            ModuleVersionArtifactMetaData possibleJarArtifact = createArtifactMetaData(module, "jar", null);
            if (artifactExists(possibleJarArtifact)) {
                return ImmutableSet.of(possibleJarArtifact);
            }
        }
        return Collections.emptySet();
    }

    private ModuleVersionArtifactMetaData createArtifactMetaData(ModuleVersionMetaData module, String type, String classifier) {
        Map extraAttributes = classifier == null ? Collections.emptyMap() : Collections.singletonMap("m:classifier", classifier);
        Artifact artifact = new DefaultArtifact(module.getDescriptor().getModuleRevisionId(), null,
                module.getId().getName(), type, "jar", extraAttributes);
        return module.artifact(artifact);
    }

    protected static class TimestampedModuleSource implements ModuleSource {
        public String getTimestampedVersion() {
            return timestampedVersion;
        }

        private final String timestampedVersion;

        public TimestampedModuleSource(String uniqueSnapshotVersion) {
            this.timestampedVersion = uniqueSnapshotVersion;
        }
    }
}
