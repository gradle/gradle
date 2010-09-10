/*
 * Copyright 2007 the original author or authors.
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

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.lock.NoLockStrategy;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.GroovyMavenDeployer;
import org.gradle.api.artifacts.maven.MavenResolver;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.publish.maven.DefaultArtifactPomFactory;
import org.gradle.api.internal.artifacts.publish.maven.DefaultMavenPomFactory;
import org.gradle.api.internal.artifacts.publish.maven.MavenPomMetaInfoProvider;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.artifacts.publish.maven.dependencies.DefaultPomDependenciesConverter;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BasePomFilterContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.DefaultArtifactPomContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenDeployer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.DeleteOnExit;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultResolverFactory implements ResolverFactory {
    private final Factory<? extends LoggingManagerInternal> loggingManagerFactory;

    public DefaultResolverFactory(Factory<? extends LoggingManagerInternal> loggingManagerFactory) {
        this.loggingManagerFactory = loggingManagerFactory;
    }

    public DependencyResolver createResolver(Object userDescription) {
        DependencyResolver result;
        if (userDescription instanceof String) {
            result = createMavenRepoResolver((String) userDescription, (String) userDescription);
        } else if (userDescription instanceof Map) {
            Map<String, String> userDescriptionMap = (Map<String, String>) userDescription;
            result = createMavenRepoResolver(userDescriptionMap.get(ResolverContainer.RESOLVER_NAME),
                    userDescriptionMap.get(ResolverContainer.RESOLVER_URL));
        } else if (userDescription instanceof DependencyResolver) {
            result = (DependencyResolver) userDescription;
        } else {
            throw new InvalidUserDataException("Illegal Resolver type");
        }
        return result;
    }

    public FileSystemResolver createFlatDirResolver(String name, File... roots) {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName(name);
        for (File root : roots) {
            String pattern = root.getAbsolutePath() + "/" + ResolverContainer.FLAT_DIR_RESOLVER_PATTERN;
            resolver.addArtifactPattern(pattern);
        }
        resolver.setValidate(false);
        resolver.setRepositoryCacheManager(createUseOriginCacheManager(name));
        return resolver;
    }

    private RepositoryCacheManager createUseOriginCacheManager(String name) {
        File tmpIvyCache = createTmpDir();
        DefaultRepositoryCacheManager cacheManager = new DefaultRepositoryCacheManager();
        cacheManager.setBasedir(tmpIvyCache);
        cacheManager.setName(name);
        cacheManager.setUseOrigin(true);
        cacheManager.setLockStrategy(new NoLockStrategy());
        cacheManager.setIvyPattern(ResolverContainer.DEFAULT_CACHE_IVY_PATTERN);
        cacheManager.setArtifactPattern(ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        return cacheManager;
    }

    private File createTmpDir() {
        File tmpFile;
        try {
            tmpFile = File.createTempFile("gradle_ivy_cache", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        tmpFile.delete();
        tmpFile.mkdir();
        DeleteOnExit.addFile(tmpFile);
        return tmpFile;
    }

    public AbstractResolver createMavenRepoResolver(String name, String root, String... jarRepoUrls) {
        GradleIBiblioResolver iBiblioResolver = createIBiblioResolver(name, root);
        if (jarRepoUrls.length == 0) {
            iBiblioResolver.setDescriptor(IBiblioResolver.DESCRIPTOR_OPTIONAL);
            return iBiblioResolver;
        }
        iBiblioResolver.setName(iBiblioResolver.getName() + "_poms");
        URLResolver urlResolver = createUrlResolver(name, root, jarRepoUrls);
        return createDualResolver(name, iBiblioResolver, urlResolver);
    }

    private GradleIBiblioResolver createIBiblioResolver(String name, String root) {
        GradleIBiblioResolver iBiblioResolver = new GradleIBiblioResolver();
        iBiblioResolver.setUsepoms(true);
        iBiblioResolver.setName(name);
        iBiblioResolver.setRoot(root);
        iBiblioResolver.setPattern(ResolverContainer.MAVEN_REPO_PATTERN);
        iBiblioResolver.setM2compatible(true);
        iBiblioResolver.setUseMavenMetadata(true);
        return iBiblioResolver;
    }

    private URLResolver createUrlResolver(String name, String root, String... jarRepoUrls) {
        URLResolver urlResolver = new URLResolver();
        urlResolver.setName(name + "_jars");
        urlResolver.setM2compatible(true);
        urlResolver.addArtifactPattern(root + '/' + ResolverContainer.MAVEN_REPO_PATTERN);
        for (String jarRepoUrl : jarRepoUrls) {
            urlResolver.addArtifactPattern(jarRepoUrl + '/' + ResolverContainer.MAVEN_REPO_PATTERN);
        }
        return urlResolver;
    }

    private DualResolver createDualResolver(String name, GradleIBiblioResolver iBiblioResolver, URLResolver urlResolver) {
        DualResolver dualResolver = new DualResolver();
        dualResolver.setName(name);
        dualResolver.setIvyResolver(iBiblioResolver);
        dualResolver.setArtifactResolver(urlResolver);
        dualResolver.setDescriptor(DualResolver.DESCRIPTOR_OPTIONAL);
        return dualResolver;
    }

    // todo use MavenPluginConvention pom factory after modularization is done

    public GroovyMavenDeployer createMavenDeployer(String name, MavenPomMetaInfoProvider pomMetaInfoProvider,
                                                   ConfigurationContainer configurationContainer,
                                                   Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver) {
        PomFilterContainer pomFilterContainer = new BasePomFilterContainer(
                new DefaultMavenPomFactory(configurationContainer, scopeMapping, new DefaultPomDependenciesConverter(
                        new DefaultExcludeRuleConverter()), fileResolver));
        return new DefaultGroovyMavenDeployer(name, pomFilterContainer, new DefaultArtifactPomContainer(
                pomMetaInfoProvider, pomFilterContainer, new DefaultArtifactPomFactory()), loggingManagerFactory.create());
    }

    // todo use MavenPluginConvention pom factory after modularization is done

    public MavenResolver createMavenInstaller(String name, MavenPomMetaInfoProvider pomMetaInfoProvider,
                                              ConfigurationContainer configurationContainer,
                                              Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver) {
        PomFilterContainer pomFilterContainer = new BasePomFilterContainer(
                new DefaultMavenPomFactory(configurationContainer, scopeMapping, new DefaultPomDependenciesConverter(
                        new DefaultExcludeRuleConverter()), fileResolver));
        return new BaseMavenInstaller(name, pomFilterContainer, new DefaultArtifactPomContainer(pomMetaInfoProvider,
                pomFilterContainer, new DefaultArtifactPomFactory()), loggingManagerFactory.create());
    }
}
