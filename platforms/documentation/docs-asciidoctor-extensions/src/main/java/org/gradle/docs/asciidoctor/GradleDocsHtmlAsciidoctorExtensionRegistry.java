/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.docs.asciidoctor;

import org.apache.commons.io.IOUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.extension.JavaExtensionRegistry;
import org.asciidoctor.jruby.extension.spi.ExtensionRegistry;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class GradleDocsHtmlAsciidoctorExtensionRegistry implements ExtensionRegistry {

    private static final String HEAD_HTML_PATH = "/head.html";
    private static final String HEADER_HTML_PATH = "/header.html";
    private static final String FOOTER_HTML_PATH = "/footer.html";

    private String headHtml;
    private String headerHtml;
    private String footerHtml;


    @Override
    public void register(Asciidoctor asciidoctor) {
        initializeHtmlToInject();

        JavaExtensionRegistry registry = asciidoctor.javaExtensionRegistry();

        registry.docinfoProcessor(new NavigationDocinfoProcessor(new HashMap<>(), headHtml));

        registry.postprocessor(new HeaderInjectingPostprocessor(new HashMap<>(), headerHtml));

        Map<String, Object> footerOptions = new HashMap<>();
        footerOptions.put("location", ":footer");
        registry.docinfoProcessor(new NavigationDocinfoProcessor(footerOptions, footerHtml));
    }


    private void initializeHtmlToInject() {
        headHtml = loadResource(HEAD_HTML_PATH);
        headerHtml = loadResource(HEADER_HTML_PATH);
        footerHtml = loadResource(FOOTER_HTML_PATH);
    }

    private String loadResource(String resourcePath) {
        try {
            URL in = getClass().getResource(resourcePath);
            if (in == null) {
                System.out.println("Docs Asciidoctor Extension did not find a resource for " + resourcePath);
                return "";
            }
            return IOUtils.toString(in, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Could not read HTML file at " + resourcePath);
        }
    }
}
