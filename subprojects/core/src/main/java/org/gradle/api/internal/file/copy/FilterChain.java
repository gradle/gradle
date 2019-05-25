/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import org.apache.tools.ant.util.ReaderInputStream;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.ConfigureUtil;

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.Map;

public class FilterChain implements Transformer<InputStream, InputStream> {
    private final ChainingTransformer<Reader> transformers = new ChainingTransformer<Reader>(Reader.class);
    private final String charset;

    public FilterChain() {
        this(Charset.defaultCharset().name());
    }

    public FilterChain(String charset) {
        this.charset = charset;
    }

    /**
     * Transforms the given Reader. The original Reader will be closed by the returned Reader.
     */
    public Reader transform(Reader original) {
        return transformers.transform(original);
    }

    /**
     * Transforms the given InputStream. The original InputStream will be closed by the returned InputStream.
     */
    @Override
    public InputStream transform(InputStream original) {
        try {
            return new ReaderInputStream(transform(new InputStreamReader(original, charset)), charset);
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasFilters() {
        return transformers.hasTransformers();
    }

    public void add(Class<? extends FilterReader> filterType) {
        add(filterType, null);
    }

    public void add(final Class<? extends FilterReader> filterType, final Map<String, ?> properties) {
        transformers.add(new Transformer<Reader, Reader>() {
            @Override
            public Reader transform(Reader original) {
                try {
                    Constructor<? extends FilterReader> constructor = filterType.getConstructor(Reader.class);
                    FilterReader result = constructor.newInstance(original);

                    if (properties != null) {
                        ConfigureUtil.configureByMap(properties, result);
                    }
                    return result;
                } catch (Throwable th) {
                    throw new InvalidUserDataException("Error - Invalid filter specification for " + filterType.getName(), th);
                }
            }
        });
    }

    public void add(final Transformer<String, String> transformer) {
        transformers.add(new Transformer<Reader, Reader>() {
            @Override
            public Reader transform(Reader reader) {
                return new LineFilter(reader, transformer);
            }
        });
    }

    public void add(final Closure closure) {
        add(new ClosureBackedTransformer(closure));
    }

    public void expand(final Map<String, ?> properties) {
        transformers.add(new Transformer<Reader, Reader>() {
            @Override
            public Reader transform(Reader original) {
                try {
                    Template template;
                    try {
                        SimpleTemplateEngine engine = new SimpleTemplateEngine();
                        template = engine.createTemplate(original);
                    } finally {
                        original.close();
                    }
                    StringWriter writer = new StringWriter();
                    template.make(properties).writeTo(writer);
                    return new StringReader(writer.toString());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }
}
