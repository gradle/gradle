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

package org.gradle.api.internal.dependencies

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.InvalidUserDataException
import org.apache.ivy.plugins.resolver.DualResolver
import org.gradle.api.DependencyManager
import org.apache.ivy.plugins.resolver.URLResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver

/**
 * @author Hans Dockter
 */
class ResolverFactory {
    static final String MAVEN2_PATTERN = "[organisation]/[module]/[revision]/[module]-[revision].[ext]"

    LocalReposCacheHandler localReposCacheHandler

    ResolverFactory(LocalReposCacheHandler localReposCacheHandler) {
        this.localReposCacheHandler = localReposCacheHandler
    }
    
    DependencyResolver createResolver(def userDescription) {
        DependencyResolver result
        switch (userDescription.getClass()) {
            case String: result = createMavenRepoResolver(userDescription, userDescription); break;
            case Map: result = createMavenRepoResolver(userDescription.name, userDescription.url); break;
            case DependencyResolver: result = userDescription; break;
            default: throw new InvalidUserDataException('Illegal Resolver type')
        }
        result
    }

    FileSystemResolver createFlatDirResolver(String name, File[] roots) {
        FileSystemResolver resolver = new FileSystemResolver()
        resolver.name = name
        resolver.setRepositoryCacheManager(localReposCacheHandler.cacheManager)
        roots.collect {"$it.absolutePath/$DependencyManager.FLAT_DIR_RESOLVER_PATTERN"}.each {String pattern ->
            resolver.addIvyPattern(pattern)
            resolver.addArtifactPattern(pattern)
        }
        resolver.validate = false
        resolver
    }

    /**
     * @param jarRepos A list of name-url pairs, denoting repositories to look for artifacts only. This is needed
     * if only the pom is in the MavenRepo repository (e.g. jta).
     */
    DependencyResolver createMavenRepoResolver(String name, String root, String[] jarRepoUrls) {
        def iBiblioResolver = new IBiblioResolver()
        iBiblioResolver.setUsepoms(true)
        iBiblioResolver.name = name + "_poms"
        iBiblioResolver.root = root
        iBiblioResolver.pattern = DependencyManager.MAVEN_REPO_PATTERN
        iBiblioResolver.m2compatible = true
        DualResolver dualResolver = new DualResolver()
        dualResolver.name = name
        dualResolver.ivyResolver = iBiblioResolver
        def urlResolver = new URLResolver()
        urlResolver.name = name + "_jars"
        urlResolver.setM2compatible(true)
        urlResolver.addArtifactPattern(root + '/' + DependencyManager.MAVEN_REPO_PATTERN)
        jarRepoUrls.each {String urlRoot ->
            urlResolver.addArtifactPattern(urlRoot + '/' + DependencyManager.MAVEN_REPO_PATTERN)
        }
        dualResolver.artifactResolver = urlResolver
        dualResolver.allownomd = false
        dualResolver
    }
}
