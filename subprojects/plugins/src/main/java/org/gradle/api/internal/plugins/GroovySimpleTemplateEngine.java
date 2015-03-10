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

import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class GroovySimpleTemplateEngine implements TemplateEngine {
    private SimpleTemplateEngine engine = new SimpleTemplateEngine();

    public String generate(File template, Map<String, String> binding) {
        String templateText = GFileUtils.readFile(template, "UTF-8");

        try {
            Writable output = engine.createTemplate(templateText).make(binding);
            return output.toString();
        } catch(ClassNotFoundException e) {
            throw new UncheckedException(e);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
