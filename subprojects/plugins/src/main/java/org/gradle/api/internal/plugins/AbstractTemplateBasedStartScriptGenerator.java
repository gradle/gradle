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

import com.google.common.base.Joiner;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.scripting.TemplateBasedScriptGenerator;
import org.gradle.util.TextUtil;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractTemplateBasedStartScriptGenerator implements TemplateBasedScriptGenerator<StartScriptGenerationDetails> {
    private final TemplateEngine templateEngine;

    public AbstractTemplateBasedStartScriptGenerator() {
        this(new GroovySimpleTemplateEngine());
    }

    public AbstractTemplateBasedStartScriptGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void generateScript(StartScriptGenerationDetails details, Writer destination) {
        try {
            Map<String, String> binding = createBinding(details);
            String scriptContent = generateStartScriptContentFromTemplate(binding);
            writeStartScriptContent(scriptContent, destination);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String generateStartScriptContentFromTemplate(Map<String, String> binding) {
        String content = templateEngine.generate(getTemplate(), binding);
        return TextUtil.convertLineSeparators(content, getLineSeparator());
    }

    private void writeStartScriptContent(String scriptContent, Writer destination) throws IOException {
        try {
            destination.write(scriptContent);
            destination.flush();
        } finally {
            destination.close();
        }
    }

    String createJoinedAppHomeRelativePath(String scriptRelPath) {
        int depth = StringUtils.countMatches(scriptRelPath, "/");
        if (depth == 0) {
            return "";
        }

        List<String> appHomeRelativePath = new ArrayList<String>();
        for(int i = 0; i < depth; i++) {
            appHomeRelativePath.add("..");
        }

        Joiner slashJoiner = Joiner.on("/");
        return slashJoiner.join(appHomeRelativePath);
    }

    abstract String getLineSeparator();
}
