/*
 * Copyright 2025 the original author or authors.
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

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.extension.InlineMacroProcessor;
import org.asciidoctor.extension.Name;
import org.asciidoctor.extension.PositionalAttributes;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Name("javadoc")
@PositionalAttributes("alt-text")
public class JavadocLinkInlineMacro extends InlineMacroProcessor {
    private void error(String message) {
        log(new LogRecord(Severity.ERROR, message));
    }

    @Override
    public Object process(ContentNode parent, String target, Map<String, Object> attributes) {

        final String href;
        final String defaultText;

        String javadocPath = String.valueOf(parent.getDocument().getAttribute("javadocpath"));
        if (javadocPath == null) {
            error("javadocpath attribute is required");
        }

        Parsed parsed = parse(target);
        if (parsed instanceof ClassOnly classOnly) {
            String basePath = classOnly.className() + ".html";
//            verifyLocationExists(javadocPath, basePath);

            href = basePath;
            defaultText = "<code>" + classOnly.simpleName() + "</code>";
        } else if (parsed instanceof ClassAndMethod classAndMethod) {
            String basePath = classAndMethod.classInfo().className() + ".html";
            String anchor = "#" + classAndMethod.methodName() + "(" + String.join(",", classAndMethod.args()) + ")";

//            Path location = verifyLocationExists(javadocPath, basePath);
//            try {
//                // Crude check that the HTML file actually has an anchor that we're trying to link to
//                if (!Files.readString(location).contains("<a href=\"" + anchor + "\"")) {
//                    error(anchor + " doesn't seem to exist in javadoc: " + location);
//                }
//            } catch (IOException e) {
//                error(e.getMessage());
//            }

            href = basePath + anchor;
            defaultText = "<code>" + classAndMethod.classInfo().simpleName() + "." + classAndMethod.methodName() + "(" + String.join(",", classAndMethod.simpleArgs()) + ")</code>";
        } else {
            throw new IllegalArgumentException("Unknown parsed type: " + parsed.getClass().getName());
        }

        Map<String, Object> options = new HashMap<>();
        options.put("type", ":link");
        options.put("target", javadocPath + "/" + href);

        final String text;
        if (attributes.containsKey("alt-text")) {
            text = attributes.get("alt-text").toString();
        } else {
            text = defaultText;
        }
        return createPhraseNode(parent, "anchor", text, attributes, options);
    }

    @SuppressWarnings("unused")
    private static Path verifyLocationExists(String javadocPath, String basePath) {
        Path location = new File(javadocPath + "/" + basePath).toPath();
        if (!Files.exists(location))  {
            throw new IllegalArgumentException("javadoc for class does not exist: " + location.toAbsolutePath());
        }
        return location;
    }

    static Parsed parse(String target) {
        // - link:{javadocPath}/org/gradle/api/artifacts/ComponentSelectionRules.html#withModule(java.lang.Object,java.lang.Object)[`ComponentSelectionRules.withModule(Object,Object)`]
        // javadoc:org.gradle.api.artifacts.dsl.ComponentMetadataHandler#all(java.lang.Class,org.gradle.api.Action)
        String[] parts = target.split("#");
        if (parts.length == 1) {
            // class only
            final String className = parts[0];
            validateClassName(target, className);
            String simpleName = className.substring(className.lastIndexOf('.')+1);
            return new ClassOnly(className.replace('.', '/'), simpleName);
        } else if (parts.length == 2) {
            final String className = parts[0];
            validateClassName(target, className);
            String simpleName = className.substring(className.lastIndexOf('.')+1);

            String[] methodParts = parts[1].replace('(', ' ').replace(')', ' ').split(" ");
            String methodName = methodParts[0];
            String[] args = methodParts[1].split(",");
            for (String arg : args) {
                validateClassName(target, arg);
            }
            List<String> simpleArgs = Arrays.stream(args).map(s -> s.substring(s.lastIndexOf('.') + 1)).collect(Collectors.toList());
            return new ClassAndMethod(new ClassOnly(className.replace('.', '/'), simpleName), methodName, args, simpleArgs);
        } else {
            // don't know how to handle this
            throw new IllegalArgumentException("don't know how to parse " + target);
        }
    }

    private static void validateClassName(String target, String className) {
        if (className.contains("/")) {
            throw new IllegalArgumentException("Separate packages with '.' and not '/'. '" + target + "' uses '/' for '" + className + "'.");
        }
        if (!className.contains(".")) {
            throw new IllegalArgumentException("Need to fully qualify all types. '" + target + "' does not fully qualify type '" + className + "'.");
        }
    }

    interface Parsed {}
    record ClassOnly(String className, String simpleName) implements Parsed {}
    record ClassAndMethod(ClassOnly classInfo, String methodName, String[] args, List<String> simpleArgs) implements Parsed {}
}
