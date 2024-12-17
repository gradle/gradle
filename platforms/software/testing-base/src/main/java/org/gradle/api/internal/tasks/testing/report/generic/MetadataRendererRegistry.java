/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.RegularFile;
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A registry of {@link MetadataRenderer} instances, used to determing how to render custom HTML
 * for a given type of test metadata when generating a test report.
 */
@ServiceScope(Scope.Global.class)
public final class MetadataRendererRegistry {
    private static final MetadataRenderer UNKNOWN_TYPE_RENDERER = new UnknownTypeRenderer();

    private final Set<MetadataRenderer> registeredRenderers = new HashSet<>();
    private final LoadingCache<String, MetadataRenderer> rendererLookupCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .build(new CacheLoader<String, MetadataRenderer>() {
            @Override
            public MetadataRenderer load(String metadataTypeName) {
                Class<?> type;
                try {
                    type = Class.forName(metadataTypeName);
                } catch (ClassNotFoundException e) {
                    return UNKNOWN_TYPE_RENDERER;
                }

                return registeredRenderers.stream()
                    .filter(r -> r.getMetadataTypes().stream().anyMatch(t -> t.isAssignableFrom(type)))
                    .findFirst()
                    .orElse(UNKNOWN_TYPE_RENDERER);
            }
        });

    public MetadataRendererRegistry() {
        registerRenderer(new BasicRenderer());
        registerRenderer(new ClickableLinkRenderer());
    }

    /**
     * Registers a new {@link MetadataRenderer} instance with this registry.
     * <p>
     * The types returned by {@link MetadataRenderer#getMetadataTypes()} should not be assignable from any other
     * types returned by the same method called on already registered renderers.
     *
     * @param metadataRenderer the renderer to register
     */
    public void registerRenderer(MetadataRenderer metadataRenderer) {
        registeredRenderers.add(metadataRenderer);
    }

    /**
     * Returns the {@link MetadataRenderer} instance that can render the given type of metadata.
     *
     * @param metadataTypeName the fully qualified name of the metadata type
     * @return the renderer for the given type
     */
    public MetadataRenderer getRenderer(String metadataTypeName) {
        return rendererLookupCache.getUnchecked(metadataTypeName);
    }

    /**
     * Defines the contract for a renderer that can render specific types of test metadata.
     */
    public interface MetadataRenderer {
        int MAX_DISPLAYABLE_LENGTH = 100;

        /**
         * Returns the types of metadata that this renderer can render.
         *
         * @return the handled types of metadata
         */
        Set<Class<?>> getMetadataTypes();

        /**
         * Renders the given metadata to the given {@link SimpleHtmlWriter}.
         *
         * @param metadata the metadata to render
         * @param htmlWriter the writer to render the metadata to
         * @return the given writer, for method chaining
         * @throws IOException if an error occurs while writing to the writer
         */
        SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException;

        static String trimIfNecessary(String input) {
            if (input.length() > MAX_DISPLAYABLE_LENGTH) {
                return input.substring(0, MAX_DISPLAYABLE_LENGTH) + "...";
            } else {
                return input;
            }
        }
    }

    public static final class UnknownTypeRenderer implements MetadataRenderer {
        @Override
        public Set<Class<?>> getMetadataTypes() {
            return Collections.singleton(Object.class);
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter
                .startElement("span").attribute("class", "unrenderable")
                .characters("[unrenderable type]")
                .endElement();
        }
    };

    public static final class BasicRenderer implements MetadataRenderer {
        private final Set<Class<?>> metadataTypes = ImmutableSet.of(String.class, Number.class, Boolean.class);

        @Override
        public Set<Class<?>> getMetadataTypes() {
            return metadataTypes;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter.characters(MetadataRenderer.trimIfNecessary(metadata.toString()));
        }
    }

    public static final class ClickableLinkRenderer implements MetadataRenderer {
        private final Set<Class<?>> metadataTypes = ImmutableSet.of(URI.class, File.class, RegularFile.class);

        @Override
        public Set<Class<?>> getMetadataTypes() {
            return metadataTypes;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            String text;
            String link;
            if (metadata instanceof File) {
                link = ((File) metadata).toURI().toASCIIString();
                text = ((File) metadata).getName();
            } else if (metadata instanceof RegularFile) {
                link = ((RegularFile) metadata).getAsFile().toURI().toASCIIString();
                text = ((RegularFile) metadata).getAsFile().getName();
            } else {
                if (((URI) metadata).getScheme().equals("file")) {
                    link = metadata.toString();
                    text = new File((URI) metadata).getName();
                } else {
                    link = metadata.toString();
                    text = metadata.toString();
                }
            }

            return (SimpleHtmlWriter) htmlWriter.startElement("a").attribute("href", link)
                .characters(MetadataRenderer.trimIfNecessary(text))
            .endElement();
        }
    }
}
