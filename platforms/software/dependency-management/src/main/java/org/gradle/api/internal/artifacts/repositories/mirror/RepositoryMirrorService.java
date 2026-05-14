/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.mirror;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.authentication.AllSchemesAuthentication;
import org.gradle.internal.credentials.DefaultPasswordCredentials;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Loads {@code mirrors.toml} once per build and rewrites resolution-aware repository lists so
 * matching Maven/Ivy repositories get redirected to a mirror.
 */
@ServiceScope(Scope.Build.class)
public class RepositoryMirrorService {

    public static final String SYS_PROP_KEY = "org.gradle.mirrors.file";
    public static final String ENV_VAR_KEY = "GRADLE_MIRRORS_FILE";
    public static final String DEFAULT_FILE_NAME = "mirrors.toml";

    private final ProviderFactory providerFactory;
    private final Instantiator instantiator;
    private final List<MirrorDefinition> mirrors;

    @Inject
    public RepositoryMirrorService(GradleUserHomeDirProvider userHomeProvider, ProviderFactory providerFactory, InstantiatorFactory instantiatorFactory) {
        this.providerFactory = providerFactory;
        this.instantiator = instantiatorFactory.decorateLenient();
        this.mirrors = loadMirrors(userHomeProvider, providerFactory);
    }

    private static List<MirrorDefinition> loadMirrors(GradleUserHomeDirProvider userHomeProvider, ProviderFactory providerFactory) {
        Path file = resolveMirrorsFile(userHomeProvider, providerFactory);
        if (file == null || !Files.exists(file)) {
            return Collections.emptyList();
        }
        return MirrorsTomlParser.parse(file);
    }

    @Nullable
    private static Path resolveMirrorsFile(GradleUserHomeDirProvider userHomeProvider, ProviderFactory providerFactory) {
        String sysProp = providerFactory.systemProperty(SYS_PROP_KEY).getOrNull();
        if (sysProp != null && !sysProp.isEmpty()) {
            return Paths.get(sysProp);
        }
        String envVar = providerFactory.environmentVariable(ENV_VAR_KEY).getOrNull();
        if (envVar != null && !envVar.isEmpty()) {
            return Paths.get(envVar);
        }
        File userHome = userHomeProvider.getGradleUserHomeDirectory();
        if (userHome == null) {
            return null;
        }
        return userHome.toPath().resolve(DEFAULT_FILE_NAME);
    }

    public boolean hasMirrors() {
        return !mirrors.isEmpty();
    }

    public List<MirrorDefinition> getMirrors() {
        return mirrors;
    }

    /**
     * Rewrites the given repository collection: any repository with a URL matching a mirror's
     * {@code match-url} pattern is replaced by a {@link MirroringResolutionAwareRepository}
     * that produces resolvers pointed at the mirror URL with mirror credentials. Other
     * repositories pass through unchanged.
     *
     * <p>The user's declared repository instances are never mutated.
     *
     * <p>TODO(v1.2): emit a Develocity build operation per rewrite recording
     * {@code originalUrl}, {@code mirrorName}, {@code mirrorUrl}, and the matcher pattern.
     */
    public Collection<? extends ResolutionAwareRepository> rewrite(Collection<? extends ResolutionAwareRepository> repositories) {
        if (mirrors.isEmpty()) {
            return repositories;
        }
        List<ResolutionAwareRepository> rewritten = new ArrayList<>(repositories.size());
        for (ResolutionAwareRepository repository : repositories) {
            ResolutionAwareRepository possiblyMirrored = mirrorIfApplicable(repository);
            rewritten.add(possiblyMirrored);
        }
        return rewritten;
    }

    private ResolutionAwareRepository mirrorIfApplicable(ResolutionAwareRepository repository) {
        URI url = extractUrl(repository);
        if (url == null) {
            return repository;
        }
        for (MirrorDefinition mirror : mirrors) {
            if (mirror.matches(url)) {
                Collection<Authentication> mirrorAuth = resolveAuthentication(mirror);
                return new MirroringResolutionAwareRepository(repository, mirror, mirrorAuth);
            }
        }
        return repository;
    }

    @Nullable
    private static URI extractUrl(ResolutionAwareRepository repository) {
        if (repository instanceof DefaultMavenArtifactRepository) {
            return ((DefaultMavenArtifactRepository) repository).getUrl();
        }
        if (repository instanceof DefaultIvyArtifactRepository) {
            return ((DefaultIvyArtifactRepository) repository).getUrl();
        }
        return null;
    }

    private Collection<Authentication> resolveAuthentication(MirrorDefinition mirror) {
        MirrorCredentialReferences refs = mirror.getCredentials();
        if (refs == null) {
            return Collections.emptyList();
        }
        String username = refs.getUsername().resolve(providerFactory).getOrNull();
        String password = refs.getPassword().resolve(providerFactory).getOrNull();
        if (username == null || password == null) {
            throw new InvalidUserDataException(
                "Mirror '" + mirror.getName() + "' references unresolved credentials: "
                    + (username == null ? refs.getUsername() : refs.getPassword())
                    + ". Set the referenced value before running the build.");
        }
        DefaultPasswordCredentials credentials = instantiator.newInstance(DefaultPasswordCredentials.class);
        credentials.setUsername(username);
        credentials.setPassword(password);
        return Collections.singleton(new AllSchemesAuthentication(credentials));
    }
}
