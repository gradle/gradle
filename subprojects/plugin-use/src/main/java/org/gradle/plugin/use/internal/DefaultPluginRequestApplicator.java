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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
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
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.repository.PluginRepository;
import org.gradle.plugin.repository.internal.BackedByArtifactRepository;
import org.gradle.plugin.use.resolve.internal.NotNonCorePluginOnClasspathCheckPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.CollectionUtils.any;
import static org.gradle.util.CollectionUtils.collect;

public class DefaultPluginRequestApplicator implements PluginRequestApplicator {
    private final PluginRegistry pluginRegistry;
    private final PluginResolverFactory pluginResolverFactory;
    private PluginRepositoryRegistry pluginRepositoryRegistry;
    private final CachedClasspathTransformer cachedClasspathTransformer;

    public DefaultPluginRequestApplicator(PluginRegistry pluginRegistry, PluginResolverFactory pluginResolver, PluginRepositoryRegistry pluginRepositoryRegistry, CachedClasspathTransformer cachedClasspathTransformer) {
        this.pluginRegistry = pluginRegistry;
        this.pluginResolverFactory = pluginResolver;
        this.pluginRepositoryRegistry = pluginRepositoryRegistry;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
    }

    public void applyPlugins(final PluginRequests requests, final ScriptHandlerInternal scriptHandler, @Nullable final PluginManagerInternal target, ClassLoaderScope classLoaderScope) {
        if (requests.isEmpty()) {
            defineScriptHandlerClassScope(scriptHandler, classLoaderScope, Collections.<PluginImplementation<?>>emptyList());
            return;
        }

        if (target == null) {
            throw new IllegalStateException("Plugin target is 'null' and there are plugin requests");
        }

        final PluginResolver effectivePluginResolver = wrapInNotInClasspathCheck(classLoaderScope);

        List<Result> results = collect(requests, new Transformer<Result, PluginRequest>() {
            public Result transform(PluginRequest request) {
                return resolveToFoundResult(effectivePluginResolver, request);
            }
        });

        // Could be different to ids in the requests as they may be unqualified
        final Map<Result, PluginId> legacyActualPluginIds = Maps.newLinkedHashMap();
        final Map<Result, PluginImplementation<?>> pluginImpls = Maps.newLinkedHashMap();
        final Map<Result, PluginImplementation<?>> pluginImplsFromOtherLoaders = Maps.newLinkedHashMap();

        if (!results.isEmpty()) {
            final RepositoryHandler repositories = scriptHandler.getRepositories();

            List<ArtifactRepository> pluginArtifactRepositories = new ArrayList<ArtifactRepository>();
            pluginRepositoryRegistry.lock();
            for (PluginRepository pluginRepository : pluginRepositoryRegistry.getPluginRepositories()) {
                if (pluginRepository instanceof BackedByArtifactRepository) {
                    pluginArtifactRepositories.add(((BackedByArtifactRepository) pluginRepository).createArtifactRepository(repositories));
                }
            }

            // The plugin repositories were appended as they were added, but we want them at the front.
            repositories.removeAll(pluginArtifactRepositories);
            repositories.addAll(0, pluginArtifactRepositories);

            final List<MavenArtifactRepository> mavenRepos = repositories.withType(MavenArtifactRepository.class);
            final Set<String> repoUrls = Sets.newLinkedHashSet();

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

            for (final String m2RepoUrl : repoUrls) {
                boolean repoExists = any(mavenRepos, new Spec<MavenArtifactRepository>() {
                    public boolean isSatisfiedBy(MavenArtifactRepository element) {
                        return element.getUrl().toString().equals(m2RepoUrl);
                    }
                });
                if (!repoExists) {
                    repositories.maven(new Action<MavenArtifactRepository>() {
                        public void execute(MavenArtifactRepository mavenArtifactRepository) {
                            mavenArtifactRepository.setUrl(m2RepoUrl);
                        }
                    });
                }
            }
        }

        defineScriptHandlerClassScope(scriptHandler, classLoaderScope, pluginImplsFromOtherLoaders.values());

        // We're making an assumption here that the target's plugin registry is backed classLoaderScope.
        // Because we are only build.gradle files right now, this holds.
        // It won't for arbitrary scripts though.
        for (final Map.Entry<Result, PluginId> entry : legacyActualPluginIds.entrySet()) {
            final PluginRequest request = entry.getKey().request;
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
        return new NotNonCorePluginOnClasspathCheckPluginResolver(pluginResolverFactory.create(), pluginRegistry, scriptClasspathPluginDescriptorLocator);
    }

    private void applyPlugin(PluginRequest request, PluginId id, Runnable applicator) {
        try {
            try {
                applicator.run();
            } catch (UnknownPluginException e) {
                throw new InvalidPluginException(
                    String.format(
                        "Could not apply requested plugin %s as it does not provide a plugin with id '%s'."
                            + " This is caused by an incorrect plugin implementation."
                            + " Please contact the plugin author(s).",
                        request, id
                    ),
                    e
                );
            } catch (Exception e) {
                throw new InvalidPluginException(String.format("An exception occurred applying plugin request %s", request), e);
            }
        } catch (Exception e) {
            throw new LocationAwareException(e, request.getScriptDisplayName(), request.getLineNumber());
        }
    }

    private Result resolveToFoundResult(PluginResolver effectivePluginResolver, PluginRequest request) {
        Result result = new Result(request);
        try {
            effectivePluginResolver.resolve(request, result);
        } catch (Exception e) {
            throw new LocationAwareException(
                new GradleException(String.format("Error resolving plugin %s", request.getDisplayName()), e),
                request.getScriptDisplayName(), request.getLineNumber());
        }

        if (!result.isFound()) {
            String message = buildNotFoundMessage(request, result);
            Exception exception = new UnknownPluginException(message);
            throw new LocationAwareException(exception, request.getScriptDisplayName(), request.getLineNumber());
        }

        return result;
    }

    private String buildNotFoundMessage(PluginRequest pluginRequest, Result result) {
        if (result.notFoundList.isEmpty()) {
            // this shouldn't happen, resolvers should call notFound()
            return String.format("Plugin %s was not found", pluginRequest.getDisplayName());
        } else {
            Formatter sb = new Formatter();
            sb.format("Plugin %s was not found in any of the following sources:%n", pluginRequest.getDisplayName());

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
        private final PluginRequest request;
        private PluginResolution found;

        public Result(PluginRequest request) {
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
