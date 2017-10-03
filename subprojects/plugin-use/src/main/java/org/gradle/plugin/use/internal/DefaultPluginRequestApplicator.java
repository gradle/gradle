/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.ClassloaderBackedPluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal;
import org.gradle.plugin.repository.PluginRepository;
import org.gradle.plugin.repository.internal.BackedByArtifactRepositories;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.ContextAwarePluginRequest;
import org.gradle.plugin.use.resolve.internal.NotNonCorePluginOnClasspathCheckPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginRequestResolutionContext;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static org.gradle.util.CollectionUtils.collect;

public class DefaultPluginRequestApplicator implements PluginRequestApplicator {
    private final PluginRegistry pluginRegistry;
    private final PluginResolverFactory pluginResolverFactory;
    private final PluginRepositoryRegistry pluginRepositoryRegistry;
    private final PluginResolutionStrategyInternal pluginResolutionStrategy;
    private final CachedClasspathTransformer cachedClasspathTransformer;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    public DefaultPluginRequestApplicator(PluginRegistry pluginRegistry, PluginResolverFactory pluginResolver, PluginRepositoryRegistry pluginRepositoryRegistry, PluginResolutionStrategyInternal pluginResolutionStrategy, CachedClasspathTransformer cachedClasspathTransformer, ProjectRegistry<ProjectInternal> projectRegistry) {
        this.pluginRegistry = pluginRegistry;
        this.pluginResolverFactory = pluginResolver;
        this.pluginRepositoryRegistry = pluginRepositoryRegistry;
        this.pluginResolutionStrategy = pluginResolutionStrategy;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
        this.projectRegistry = projectRegistry;
    }

    public void applyPlugins(final ScriptSource requestingScriptSource, final PluginRequests requests, final ScriptHandlerInternal scriptHandler, @Nullable final PluginManagerInternal target, ClassLoaderScope classLoaderScope) {
        if (requests.isEmpty()) {
            defineScriptHandlerClassScope(scriptHandler, classLoaderScope, Collections.<PluginImplementation<?>>emptyList());
            return;
        }

        if (target == null) {
            throw new IllegalStateException("Plugin target is 'null' and there are plugin requests");
        }

        final PluginResolver effectivePluginResolver = wrapInNotInClasspathCheck(classLoaderScope);

        List<Result> results = collect(requests, new Transformer<Result, PluginRequestInternal>() {
            public Result transform(PluginRequestInternal request) {
                PluginRequestInternal configuredRequest = pluginResolutionStrategy.applyTo(request);
                return resolveToFoundResult(requestingScriptSource, effectivePluginResolver, configuredRequest);
            }
        });

        // Could be different to ids in the requests as they may be unqualified
        final Map<Result, PluginId> legacyActualPluginIds = newLinkedHashMap();
        final Map<Result, PluginImplementation<?>> pluginImpls = newLinkedHashMap();
        final Map<Result, PluginImplementation<?>> pluginImplsFromOtherLoaders = newLinkedHashMap();

        if (!results.isEmpty()) {
            final RepositoryHandler repositories = scriptHandler.getRepositories();

            createPluginArtifactRepositories(repositories);

            final Set<String> repoUrls = newLinkedHashSet();

            for (final Result result : results) {
                applyPlugin(result.request, result.found.getPluginId(), new Runnable() {
                    @Override
                    public void run() {
                        result.found.execute(new PluginResolveContext() {
                            public void addLegacy(PluginId pluginId, final String m2RepoUrl, Object dependencyNotation) {
                                repoUrls.add(m2RepoUrl);
                                addLegacy(pluginId, dependencyNotation);
                            }

                            @Override
                            public void addLegacy(PluginId pluginId, Object dependencyNotation) {
                                legacyActualPluginIds.put(result, pluginId);
                                scriptHandler.addScriptClassPathDependency(dependencyNotation);
                            }

                            @Override
                            public void add(PluginImplementation<?> plugin) {
                                pluginImpls.put(result, plugin);
                            }

                            @Override
                            public void addFromDifferentLoader(PluginImplementation<?> plugin) {
                                pluginImplsFromOtherLoaders.put(result, plugin);
                            }
                        });
                    }
                });
            }

            addMissingMavenRepositories(repositories, repoUrls);
        }

        defineScriptHandlerClassScope(scriptHandler, classLoaderScope, pluginImplsFromOtherLoaders.values());

        // We're making an assumption here that the target's plugin registry is backed classLoaderScope.
        // Because we are only build.gradle files right now, this holds.
        // It won't for arbitrary scripts though.
        for (final Map.Entry<Result, PluginId> entry : legacyActualPluginIds.entrySet()) {
            final PluginRequestInternal request = entry.getKey().request;
            final PluginId id = entry.getValue();
            applyPlugin(request, id, new Runnable() {
                public void run() {
                    if (request.isApply()) {
                        target.apply(id.toString());
                    }
                }
            });
        }

        for (final Map.Entry<Result, PluginImplementation<?>> entry : Iterables.concat(pluginImpls.entrySet(), pluginImplsFromOtherLoaders.entrySet())) {
            final Result result = entry.getKey();
            applyPlugin(result.request, result.found.getPluginId(), new Runnable() {
                public void run() {
                    if (result.request.isApply()) {
                        target.apply(entry.getValue());
                    }
                }
            });
        }
    }

    private void createPluginArtifactRepositories(RepositoryHandler repositories) {
        for (PluginRepository pluginRepository : pluginRepositoryRegistry.getPluginRepositories()) {
            if (pluginRepository instanceof BackedByArtifactRepositories) {
                ((BackedByArtifactRepositories) pluginRepository).createArtifactRepositories(repositories);
            }
        }
    }

    private void addMissingMavenRepositories(RepositoryHandler repositories, Set<String> repoUrls) {
        if (repoUrls.isEmpty()) {
            return;
        }
        final Set<String> existingMavenUrls = existingMavenUrls(repositories);
        for (final String repoUrl : repoUrls) {
            if (!existingMavenUrls.contains(repoUrl)) {
                maven(repositories, repoUrl);
            }
        }
    }

    private void maven(RepositoryHandler repositories, final String m2RepoUrl) {
        repositories.maven(new Action<MavenArtifactRepository>() {
            public void execute(MavenArtifactRepository mavenArtifactRepository) {
                mavenArtifactRepository.setUrl(m2RepoUrl);
            }
        });
    }

    private Set<String> existingMavenUrls(RepositoryHandler repositories) {
        Set<String> mavenUrls = newHashSet();
        for (ArtifactRepository repo : repositories) {
            if (repo instanceof MavenArtifactRepository) {
                mavenUrls.add(((MavenArtifactRepository) repo).getUrl().toString());
            }
        }
        return mavenUrls;
    }

    private void defineScriptHandlerClassScope(ScriptHandlerInternal scriptHandler, ClassLoaderScope classLoaderScope, Iterable<PluginImplementation<?>> pluginsFromOtherLoaders) {
        ClassPath classPath = scriptHandler.getScriptClassPath();
        ClassPath cachedJarClassPath = cachedClasspathTransformer.transform(classPath);
        classLoaderScope.export(cachedJarClassPath);

        for (PluginImplementation<?> pluginImplementation : pluginsFromOtherLoaders) {
            classLoaderScope.export(pluginImplementation.asClass().getClassLoader());
        }

        classLoaderScope.lock();
    }

    private PluginResolver wrapInNotInClasspathCheck(ClassLoaderScope classLoaderScope) {
        PluginDescriptorLocator scriptClasspathPluginDescriptorLocator = new ClassloaderBackedPluginDescriptorLocator(classLoaderScope.getParent().getExportClassLoader());
        PluginResolver pluginResolver = pluginResolverFactory.create();
        return new NotNonCorePluginOnClasspathCheckPluginResolver(pluginResolver, pluginRegistry, scriptClasspathPluginDescriptorLocator);
    }

    private void applyPlugin(PluginRequestInternal request, PluginId id, Runnable applicator) {
        try {
            try {
                applicator.run();
            } catch (UnknownPluginException e) {
                throw couldNotApply(request, id, e);
            } catch (Exception e) {
                throw exceptionOccurred(request, e);
            }
        } catch (Exception e) {
            throw new LocationAwareException(e, request.getRequestingScriptDisplayName(), request.getRequestingScriptLineNumber());
        }
    }

    private InvalidPluginException couldNotApply(PluginRequestInternal request, PluginId id, UnknownPluginException cause) {
        return new InvalidPluginException(
            String.format(
                "Could not apply requested plugin [%s] as it does not provide a plugin with id '%s'."
                    + " This is caused by an incorrect plugin implementation."
                    + " Please contact the plugin author(s).",
                request, id),
            cause);
    }

    private InvalidPluginException exceptionOccurred(PluginRequestInternal request, Exception e) {
        return new InvalidPluginException(String.format("An exception occurred applying plugin request [%s]", request), e);
    }

    private Result resolveToFoundResult(ScriptSource requestingScriptSource, PluginResolver effectivePluginResolver, PluginRequestInternal request) {
        Result result = new Result(request);
        try {
            effectivePluginResolver.resolve(createContextAwarePluginRequest(request, requestingScriptSource), result);
        } catch (Exception e) {
            throw new LocationAwareException(
                new GradleException(String.format("Error resolving plugin [%s]", request.getDisplayName()), e),
                requestingScriptSource.getDisplayName(), request.getRequestingScriptLineNumber());
        }

        if (!result.isFound()) {
            String message = buildNotFoundMessage(request, result);
            Exception exception = new UnknownPluginException(message);
            throw new LocationAwareException(exception, requestingScriptSource.getDisplayName(), request.getRequestingScriptLineNumber());
        }

        return result;
    }

    private ContextAwarePluginRequest createContextAwarePluginRequest(PluginRequestInternal request, ScriptSource requestingScriptSource) {
        File rootDir = projectRegistry.getRootProject().getRootDir();
        ResourceLocation requestingScriptLocation = requestingScriptSource.getResource().getLocation();
        PluginRequestResolutionContext resolutionContext = new PluginRequestResolutionContext(rootDir, requestingScriptLocation);
        return new ContextAwarePluginRequest(request, resolutionContext);
    }

    private String buildNotFoundMessage(PluginRequestInternal pluginRequest, Result result) {
        if (result.notFoundList.isEmpty()) {
            // this shouldn't happen, resolvers should call notFound()
            return String.format("Plugin [%s] was not found", pluginRequest.getDisplayName());
        } else {
            Formatter sb = new Formatter();
            sb.format("Plugin [%s] was not found in any of the following sources:%n", pluginRequest.getDisplayName());

            for (NotFound notFound : result.notFoundList) {
                sb.format("%n- %s", notFound.source);
                if (notFound.detail != null) {
                    sb.format(" (%s)", notFound.detail);
                }
            }

            return sb.toString();
        }
    }

    private static class NotFound {
        private final String source;
        private final String detail;

        private NotFound(String source, String detail) {
            this.source = source;
            this.detail = detail;
        }
    }

    private static class Result implements PluginResolutionResult {
        private final List<NotFound> notFoundList = new LinkedList<NotFound>();
        private final PluginRequestInternal request;
        private PluginResolution found;

        public Result(PluginRequestInternal request) {
            this.request = request;
        }

        public void notFound(String sourceDescription, String notFoundDetail) {
            notFoundList.add(new NotFound(sourceDescription, notFoundDetail));
        }

        public void found(String sourceDescription, PluginResolution pluginResolution) {
            found = pluginResolution;
        }

        public boolean isFound() {
            return found != null;
        }
    }
}
