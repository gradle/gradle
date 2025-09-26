/*
 * Copyright 2025 Gradle and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.docs.asciidoctor;

import org.apache.commons.io.IOUtils;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Postprocessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * This processor adds the ability to copy code blocks to the clipboard
 */
public class ClipboardPostprocessor extends Postprocessor {

    public ClipboardPostprocessor(){ }

    @Override
    public String process(Document document, String output) {

        if (!document.isBasebackend("html")) {
            return output;
        }

        Pattern codeBlockPattern = Pattern.compile("</code>", Pattern.DOTALL);
        Matcher matcher = codeBlockPattern.matcher(output);
        if (!matcher.find()) {
            return output; // No <code> block found
        }

        output = addExternalJs(output, "https://cdnjs.cloudflare.com/ajax/libs/jquery/3.7.1/jquery.min.js");
        output = addExternalJs(output, "https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/2.0.11/clipboard.min.js");
        output = addJs(output, "/clipboard.js");
        output = addCss(output, "/clipboard.css");

        return output;
    }

    private static String readResource(String resourcePath) {
        try (InputStream inputStream = ClipboardPostprocessor.class.getResourceAsStream(resourcePath)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read source resource for Clipboard: " + e.getMessage());
        }
    }

    private static String addCss(String output, String resource){
        String css = readResource(resource);
        String replacement = new StringBuffer()
            .append("<style>").append(css).append("</style>")
            .append("</head>")
            .toString();
        return  output.replace("</head>", replacement);
    }

    private static String addJs(String output, String resource){
        String javascript = readResource(resource);
        String replacement = new StringBuffer()
            .append("<script type='text/javascript'>").append(javascript).append("</script>")
            .append("</html>")
            .toString();
        return output.replace("</html>", replacement);
    }

    private static String addExternalJs(String output, String url){
        String replacement = new StringBuffer()
            .append("</body>")
            .append("<script type='text/javascript' src='").append(url).append("'></script>")
            .toString();
        return output.replace("</body>", replacement);
    }

}

