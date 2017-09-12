/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal;

import com.google.common.io.Files;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheKeyBuilder;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.resource.TextResourceLoader;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.hash.HashUtil.createHash;

public class ScriptPluginLoaderCache {

    private static final int SCRIPT_PLUGIN_LOADERS_CACHE_VERSION = 1;

    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final CacheRepository cacheRepository;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final TextResourceLoader textResourceLoader;
    private final ScriptPluginLoaderClassGenerator generator;

    private final Map<String, ScriptPluginLoader> buildScopedMemoryCache = new HashMap<String, ScriptPluginLoader>();

    public ScriptPluginLoaderCache(ProjectRegistry<ProjectInternal> projectRegistry, CacheRepository cacheRepository, CacheKeyBuilder cacheKeyBuilder, TextResourceLoader textResourceLoader) {
        this.projectRegistry = projectRegistry;
        this.cacheRepository = cacheRepository;
        this.cacheKeyBuilder = cacheKeyBuilder;
        this.textResourceLoader = textResourceLoader;
        this.generator = new ScriptPluginLoaderClassGenerator();
    }

    public ScriptPluginLoader scriptPluginLoaderFor(ContextAwarePluginRequest pluginRequest, String displayName) {

        final String scriptContent = scriptContentFor(pluginRequest);
        final String scriptContentHash = createHash(scriptContent, "SHA1").asCompactString();

        ScriptPluginLoader scriptPluginLoader = buildScopedMemoryCache.get(scriptContentHash);
        if (scriptPluginLoader == null) {
            scriptPluginLoader = createScriptPluginLoader(displayName, scriptContent, scriptContentHash);
            buildScopedMemoryCache.put(scriptContentHash, scriptPluginLoader);
        }
        return scriptPluginLoader;
    }

    private ScriptPluginLoader createScriptPluginLoader(final String displayName, final String scriptContent, final String scriptContentHash) {

        final ScriptPluginLoaderSpec loaderSpec = new ScriptPluginLoaderSpec(displayName, scriptContent, scriptContentHash);

        CacheKeyBuilder.CacheKeySpec cacheKeySpec = CacheKeyBuilder.CacheKeySpec
            .withPrefix("script-plugin-loaders")
            .plus(String.valueOf(SCRIPT_PLUGIN_LOADERS_CACHE_VERSION))
            .plus(scriptContentHash);

        final String cacheClassPathDirName = "cp";

        PersistentCache cache = cacheRepository.cache(cacheKeyBuilder.build(cacheKeySpec))
            .withInitializer(new Action<PersistentCache>() {
                @Override
                public void execute(@Nonnull PersistentCache cache) {

                    byte[] bytes = generator.generateScriptPluginLoaderClass(loaderSpec);

                    String classFilePath = cacheClassPathDirName + "/" + loaderSpec.getLoaderClassFilePath();
                    File classFile = new File(cache.getBaseDir(), classFilePath);

                    try {
                        Files.createParentDirs(classFile);
                        Files.write(bytes, classFile);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            })
            .open();
        cache.close();

        ClassLoaderScope scriptPluginsScope = ScriptPluginsScope.from(projectRegistry);
        ClassLoaderScope loaderScope = scriptPluginsScope.createChild("script-plugin-loader-" + scriptContentHash);

        File classpathDir = new File(cache.getBaseDir(), cacheClassPathDirName);
        loaderScope.local(DefaultClassPath.of(Collections.singleton(classpathDir)));
        loaderScope.lock();

        try {
            Class<?> implementationClass = loaderScope.getLocalClassLoader().loadClass(loaderSpec.getLoaderClassBinaryName());
            return new ScriptPluginLoader(implementationClass);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String scriptContentFor(ContextAwarePluginRequest pluginRequest) {
        return textResourceLoader.loadUri("script plugin", pluginRequest.getScriptUri()).getText();
    }
}
