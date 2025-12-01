/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.plugins;

import com.google.common.io.CharSource;
import groovy.text.SimpleTemplateEngine;
import org.gradle.api.Transformer;
import org.gradle.api.internal.resources.CharSourceBackedTextResource;
import org.gradle.api.resources.TextResource;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;
import static org.gradle.util.internal.TextUtil.convertLineSeparators;

public class DefaultTemplateBasedStartScriptGenerator implements TemplateBasedScriptGenerator {

    private final String lineSeparator;
    private final Transformer<Map<String, String>, JavaAppStartScriptGenerationDetails> bindingFactory;

    private TextResource template;

    public DefaultTemplateBasedStartScriptGenerator(String lineSeparator, Transformer<Map<String, String>, JavaAppStartScriptGenerationDetails> bindingFactory, TextResource template) {
        this.lineSeparator = lineSeparator;
        this.bindingFactory = bindingFactory;
        this.template = template;
    }

    @Override
    public void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            destination.write(generateStartScriptContentFromTemplate(bindingFactory.transform(details)));
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    @Override
    public void setTemplate(TextResource template) {
        this.template = template;
    }

    @Override
    public TextResource getTemplate() {
        return template;
    }

    private String generateStartScriptContentFromTemplate(final Map<String, String> binding) {
        try (Reader reader = getTemplate().asReader()) {
            return convertLineSeparators(new SimpleTemplateEngine().createTemplate(reader).make(binding).toString(), lineSeparator);
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
    }

    protected static TextResource utf8ClassPathResource(final Class<?> clazz, final String filename) {
        return new CharSourceBackedTextResource("Classpath resource '" + filename + "'", new CharSource() {
            @Override
            public Reader openStream() {
                InputStream stream = clazz.getResourceAsStream(filename);
                if (stream == null) {
                    throw new IllegalStateException("Could not find class path resource " + filename + " relative to " + clazz.getName());
                }
                return new BufferedReader(new InputStreamReader(stream, UTF_8));
            }
        });
    }

}
