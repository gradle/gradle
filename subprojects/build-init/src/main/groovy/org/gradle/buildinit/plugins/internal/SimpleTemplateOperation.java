/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;
import groovy.util.CharsetToolkit;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.Writer;
import java.net.URL;
import java.util.Map;

public class SimpleTemplateOperation implements TemplateOperation {
    private final URL templateURL;
    private final File target;
    private final Map<String, TemplateValue> bindings;

    public SimpleTemplateOperation(URL templateURL, File target, Map<String, TemplateValue> bindings) {
        if (templateURL == null) {
            throw new BuildInitException("Template URL must not be null");
        }

        if (target == null) {
            throw new BuildInitException("Target file must not be null");
        }

        this.templateURL = templateURL;
        this.bindings = bindings;
        this.target = target;
    }

    @Override
    public void generate() {
        try {
            target.getParentFile().mkdirs();
            SimpleTemplateEngine templateEngine = new SimpleTemplateEngine();
            String templateText = Resources.asCharSource(templateURL, CharsetToolkit.getDefaultSystemCharset()).read();
            Template template = templateEngine.createTemplate(templateText);
            Writer writer = Files.asCharSink(target, Charsets.UTF_8).openStream();
            try {
                template.make(bindings).writeTo(writer);
            } finally {
                writer.close();
            }
        } catch (Exception ex) {
            throw new GradleException("Could not generate file " + target + ".", ex);
        }
    }
}
