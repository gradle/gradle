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

package org.gradle.api.internal.dependencies.ivyservice;

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.lock.NoLockStrategy;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.DependencyManager;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.maven.GroovyMavenDeployer;
import org.gradle.api.dependencies.maven.MavenResolver;
import org.gradle.api.internal.dependencies.DependencyManagerInternal;
import org.gradle.api.internal.dependencies.maven.*;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultExcludeRuleConverter;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultPomDependenciesConverter;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultPomDependenciesWriter;
import org.gradle.api.internal.dependencies.maven.deploy.BaseMavenInstaller;
import org.gradle.api.internal.dependencies.maven.deploy.DefaultArtifactPomContainer;
import org.gradle.api.internal.dependencies.maven.deploy.groovy.DefaultGroovyMavenDeployer;
import org.gradle.api.internal.dependencies.maven.deploy.groovy.DefaultGroovyPomFilterContainer;

import java.io.File;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultResolverFactory implements ResolverFactory {
    private File tmpIvyCache;

    public DefaultResolverFactory(File tmpIvyCache) {
        this.tmpIvyCache = tmpIvyCache;
    }

    public DependencyResolver createResolver(Object userDescription) {
        DependencyResolver result;
        if (userDescription instanceof String) {
            result = createMavenRepoResolver((String) userDescription, (String) userDescription);
        } else if (userDescription instanceof Map) {
            Map<String, String> userDescriptionMap = (Map<String, String>) userDescription;
            result = createMavenRepoResolver(userDescriptionMap.get("name"), userDescriptionMap.get("url"));
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
            String pattern = root.getAbsolutePath() + "/" + DependencyManager.FLAT_DIR_RESOLVER_PATTERN;
            resolver.addArtifactPattern(pattern);
        }
        resolver.setValidate(false);
        resolver.setRepositoryCacheManager(createUseOriginCacheManager(name));
        return resolver;
    }

    private RepositoryCacheManager createUseOriginCacheManager(String name) {
        DefaultRepositoryCacheManager cacheManager = new DefaultRepositoryCacheManager();
        cacheManager.setBasedir(tmpIvyCache);
        cacheManager.setName(name);
        cacheManager.setUseOrigin(true);
        cacheManager.setLockStrategy(new NoLockStrategy());
        cacheManager.setIvyPattern(DependencyManager.DEFAULT_CACHE_IVY_PATTERN);
        cacheManager.setArtifactPattern(DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN);
        return cacheManager;
    }

    public AbstractResolver createMavenRepoResolver(String name, String root, String... jarRepoUrls) {
        IBiblioResolver iBiblioResolver = new IBiblioResolver();
        iBiblioResolver.setUsepoms(true);
        iBiblioResolver.setName(name);
        iBiblioResolver.setRoot(root);
        iBiblioResolver.setPattern(DependencyManager.MAVEN_REPO_PATTERN);
        iBiblioResolver.setM2compatible(true);
        iBiblioResolver.setUseMavenMetadata(true);
        if (jarRepoUrls.length == 0) {
            iBiblioResolver.setDescriptor(IBiblioResolver.DESCRIPTOR_OPTIONAL);
            return iBiblioResolver;
        }
        iBiblioResolver.setName(iBiblioResolver.getName() + "_poms");
        DualResolver dualResolver = new DualResolver();
        dualResolver.setName(name);
        dualResolver.setIvyResolver(iBiblioResolver);
        URLResolver urlResolver = new URLResolver();
        urlResolver.setName(name + "_jars");
        urlResolver.setM2compatible(true);
        urlResolver.addArtifactPattern(root + '/' + DependencyManager.MAVEN_REPO_PATTERN);
        for (String jarRepoUrl : jarRepoUrls) {
            urlResolver.addArtifactPattern(jarRepoUrl + '/' + DependencyManager.MAVEN_REPO_PATTERN);
        }
        dualResolver.setArtifactResolver(urlResolver);
        dualResolver.setDescriptor(DualResolver.DESCRIPTOR_OPTIONAL);
        return dualResolver;
    }

    public GroovyMavenDeployer createMavenDeployer(String name, File pomDir, DependencyManagerInternal dependencyManager) {
        DefaultGroovyPomFilterContainer pomFilterContainer = new DefaultGroovyPomFilterContainer(
                new DefaultMavenPomFactory(dependencyManager.getDefaultMavenScopeMapping()));
        return new DefaultGroovyMavenDeployer(name,
                pomFilterContainer,
                new DefaultArtifactPomContainer(pomDir, pomFilterContainer,
                        new DefaultPomFileWriter(
                                new DefaultPomWriter(
                                        new DefaultPomHeaderWriter(),
                                        new DefaultPomModuleIdWriter(),
                                        new DefaultPomDependenciesWriter(
                                                new DefaultPomDependenciesConverter(
                                                        new DefaultExcludeRuleConverter()
                                                )
                                        )
                                )),
                        new DefaultArtifactPomFactory()),
                dependencyManager
        );
    }

    public MavenResolver createMavenInstaller(String name, File pomDir, DependencyManagerInternal dependencyManager) {
        DefaultGroovyPomFilterContainer pomFilterContainer = new DefaultGroovyPomFilterContainer(
                new DefaultMavenPomFactory(dependencyManager.getDefaultMavenScopeMapping()));
        return new BaseMavenInstaller(name,
                pomFilterContainer,
                new DefaultArtifactPomContainer(pomDir, pomFilterContainer,
                        new DefaultPomFileWriter(
                                new DefaultPomWriter(
                                        new DefaultPomHeaderWriter(),
                                        new DefaultPomModuleIdWriter(),
                                        new DefaultPomDependenciesWriter(
                                                new DefaultPomDependenciesConverter(
                                                        new DefaultExcludeRuleConverter()
                                                )
                                        )
                                )),
                        new DefaultArtifactPomFactory()),
                dependencyManager
        );
    }

    public File getTmpIvyCache() {
        return tmpIvyCache;
    }

    public void setTmpIvyCache(File tmpIvyCache) {
        this.tmpIvyCache = tmpIvyCache;
    }
}
