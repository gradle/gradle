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

package org.gradle.api.internal.initialization;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ProtectionDomains;
import org.gradle.internal.classloader.TransformedClassPathHolder;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.code.Locality;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class DefaultCodeLocalityResolver implements CodeLocalityResolver {

    private final Set<ClassLoader> coreClassLoaders;
    // A plugin class is asked about once for every project that applies it. The keys are weak, so a class can be unloaded while the build tree lives.
    private final ConcurrentMap<Class<?>, CodeOrigin> origins = new MapMaker().weakKeys().makeMap();

    @Inject
    public DefaultCodeLocalityResolver(ClassLoaderRegistry classLoaderRegistry) {
        this(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader());
    }

    DefaultCodeLocalityResolver(ClassLoader... coreClassLoaders) {
        ImmutableSet.Builder<ClassLoader> loaders = ImmutableSet.builder();
        for (ClassLoader loader : coreClassLoaders) {
            for (ClassLoader current = loader; current != null; current = current.getParent()) {
                loaders.add(current);
            }
        }
        this.coreClassLoaders = loaders.build();
    }

    @Override
    public Locality localityOf(Class<?> clazz) {
        return originOf(clazz).locality;
    }

    @Override
    public @Nullable File codeLocationOf(Class<?> clazz) {
        return originOf(clazz).codeLocation;
    }

    private CodeOrigin originOf(Class<?> clazz) {
        return origins.computeIfAbsent(clazz, this::findOriginOf);
    }

    private CodeOrigin findOriginOf(Class<?> clazz) {
        return new CodeOrigin(findLocalityOf(clazz), findCodeLocationOf(clazz));
    }

    private Locality findLocalityOf(Class<?> clazz) {
        ClassLoader loader = clazz.getClassLoader();
        if (coreClassLoaders.contains(loader)) {
            return Locality.CORE;
        }
        if (!(loader instanceof TransformedClassPathHolder)) {
            return Locality.UNKNOWN;
        }
        File codeSource = codeSourceOf(clazz);
        if (codeSource == null) {
            return Locality.UNKNOWN;
        }
        return localityOf(((TransformedClassPathHolder) loader).getTransformedClassPath().kindOf(codeSource));
    }

    private static @Nullable File findCodeLocationOf(Class<?> clazz) {
        try {
            CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
            URL location = codeSource == null ? null : codeSource.getLocation();
            return location != null && "file".equals(location.getProtocol()) ? new File(location.toURI()) : null;
        } catch (SecurityException | URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Locality localityOf(TransformedClassPath.InstrumentationKind kind) {
        switch (kind) {
            case PROJECT_DEPENDENCY:
                return Locality.LOCAL;
            case EXTERNAL_DEPENDENCY:
                return Locality.EXTERNAL;
            default:
                return Locality.UNKNOWN;
        }
    }

    @Nullable
    private static File codeSourceOf(Class<?> clazz) {
        try {
            return ProtectionDomains.codeSourceFileOf(clazz.getProtectionDomain());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class CodeOrigin {
        final Locality locality;
        final @Nullable File codeLocation;

        CodeOrigin(Locality locality, @Nullable File codeLocation) {
            this.locality = locality;
            this.codeLocation = codeLocation;
        }
    }
}
