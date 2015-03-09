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
import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.scripting.ScriptGenerator;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.util.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractTemplateBasedStartScriptGenerator implements ScriptGenerator<StartScriptGenerationDetails> {
    public void generateScript(StartScriptGenerationDetails details, Writer destination) {
        try {
            Map<String, String> binding = createBinding(details);
            String nativeOutput = generateStartScriptContentFromTemplate(binding);
            writeStartScriptContent(nativeOutput, destination);
        } catch(URISyntaxException e) {
            throw new UncheckedException(e);
        } catch(ClassNotFoundException e) {
            throw new UncheckedException(e);
        } catch(IOException e) {
            throw new UncheckedException(e);
        }
    }

    private String generateStartScriptContentFromTemplate(Map<String, String> binding) throws URISyntaxException, ClassNotFoundException, IOException {
        URL stream = getClass().getResource(getTemplate().getName());
        String templateText = GFileUtils.readFile(new File(stream.toURI()), "UTF-8");
        Writable output = new SimpleTemplateEngine().createTemplate(templateText).make(binding);
        return TextUtil.convertLineSeparators(output.toString(), getLineSeparator());
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
    abstract File getTemplate();
    abstract Map<String, String> createBinding(StartScriptGenerationDetails details);
}
