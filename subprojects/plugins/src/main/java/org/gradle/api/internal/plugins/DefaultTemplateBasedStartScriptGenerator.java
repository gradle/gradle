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

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.resources.CharSourceBackedTextResource;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.io.IoUtils;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;
import org.gradle.util.TextUtil;

import java.io.*;
import java.util.Map;

public class DefaultTemplateBasedStartScriptGenerator implements TemplateBasedScriptGenerator {

    private final String lineSeparator;
    private final Transformer<Map<String, String>, JavaAppStartScriptGenerationDetails> bindingFactory;

    private TextResource template;

    public DefaultTemplateBasedStartScriptGenerator(String lineSeparator, Transformer<Map<String, String>, JavaAppStartScriptGenerationDetails> bindingFactory, TextResource template) {
        this.lineSeparator = lineSeparator;
        this.bindingFactory = bindingFactory;
        this.template = template;
    }

    public void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            Map<String, String> binding = bindingFactory.transform(details);
            String scriptContent = generateStartScriptContentFromTemplate(binding);
            destination.write(scriptContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setTemplate(TextResource template) {
        this.template = template;
    }

    public TextResource getTemplate() {
        return template;
    }

    private String generateStartScriptContentFromTemplate(final Map<String, String> binding) {
        return IoUtils.get(getTemplate().asReader(), new Transformer<String, Reader>() {
            @Override
            public String transform(Reader reader) {
                try {
                    SimpleTemplateEngine engine = new SimpleTemplateEngine();
                    Template template = engine.createTemplate(reader);
                    String output = template.make(binding).toString();
                    return TextUtil.convertLineSeparators(output, lineSeparator);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    protected static TextResource utf8ClassPathResource(final Class<?> clazz, final String filename) {
        return new CharSourceBackedTextResource("Classpath resource '" + filename + "'", new CharSource() {
            @Override
            public Reader openStream() throws IOException {
                InputStream stream = clazz.getResourceAsStream(filename);
                if (stream == null) {
                    throw new IllegalStateException("Could not find class path resource " + filename + " relative to " + clazz.getName());
                }
                return new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
            }
        });
    }

}
