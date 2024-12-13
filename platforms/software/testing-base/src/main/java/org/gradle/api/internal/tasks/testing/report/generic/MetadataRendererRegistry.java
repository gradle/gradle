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
import org.gradle.internal.html.SimpleHtmlWriter;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

@ServiceScope(Scope.Global.class)
public class MetadataRendererRegistry {
    private final Set<MetadataRenderer<?>> renderers = new HashSet<>();
    private final MetadataRenderer<Object> unknownTypeRenderer = new MetadataRenderer<Object>() {
        @Override
        public Class<Object> getMetadataType() {
            return Object.class;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter
                .startElement("span").attribute("class", "unrenderable")
                    .characters("[unrenderable type]")
                .endElement();
        }
    };
    private final LoadingCache<String, MetadataRenderer<?>> rendererLookupCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .build(new CacheLoader<String, MetadataRenderer<?>>() {
            @Override
            public MetadataRenderer<?> load(String metadataTypeName) {
                return lookupRenderer(metadataTypeName);
            }
        });

    public MetadataRendererRegistry() {
        registerRenderer(new StringMetadataRenderer());
        registerRenderer(new NumberMetadataRenderer());
        registerRenderer(new URIMetadataRenderer());
    }

    public void registerRenderer(MetadataRenderer<?> metadataRenderer) {
        renderers.add(metadataRenderer);
    }

    @SuppressWarnings("unchecked")
    public <T> MetadataRenderer<T> getRenderer(String metadataTypeName) {
        return (MetadataRenderer<T>) rendererLookupCache.getUnchecked(metadataTypeName);
    }

    @SuppressWarnings("unchecked")
    private <T> MetadataRenderer<T> lookupRenderer(String metadataTypeName) {
        Class<T> type;
        try {
            type = (Class<T>) Class.forName(metadataTypeName);
        } catch (ClassNotFoundException e) {
            return (MetadataRenderer<T>) unknownTypeRenderer;
        }

        return renderers.stream().filter(r -> r.getMetadataType().isAssignableFrom(type))
            .findFirst()
            .map(r -> (MetadataRenderer<T>) r)
            .orElse((MetadataRenderer<T>) unknownTypeRenderer);
    }

    public interface MetadataRenderer<T> {
        int MAX_DISPLAYABLE_LENGTH = 100;

        Class<T> getMetadataType();
        SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException;

        static String trimIfNecessary(String input) {
            if (input.length() > MAX_DISPLAYABLE_LENGTH) {
                return input.substring(0, MAX_DISPLAYABLE_LENGTH) + "...";
            } else {
                return input;
            }
        }
    }

    public static final class StringMetadataRenderer implements MetadataRenderer<String> {
        @Override
        public Class<String> getMetadataType() {
            return String.class;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter.characters(MetadataRenderer.trimIfNecessary((String) metadata));
        }
    }

    public static final class NumberMetadataRenderer implements MetadataRenderer<Number> {
        @Override
        public Class<Number> getMetadataType() {
            return Number.class;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter.characters(MetadataRenderer.trimIfNecessary(metadata.toString()));
        }
    }

    public static final class URIMetadataRenderer implements MetadataRenderer<URI> {
        @Override
        public Class<URI> getMetadataType() {
            return URI.class;
        }

        @Override
        public SimpleHtmlWriter render(Object metadata, SimpleHtmlWriter htmlWriter) throws IOException {
            return (SimpleHtmlWriter) htmlWriter.startElement("a").attribute("href", metadata.toString())
                .characters(MetadataRenderer.trimIfNecessary(metadata.toString()))
            .endElement();
        }
    }
}
