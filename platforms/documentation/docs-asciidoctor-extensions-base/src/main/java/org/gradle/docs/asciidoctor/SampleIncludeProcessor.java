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
package org.gradle.docs.asciidoctor;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SampleIncludeProcessor extends IncludeProcessor {

    private static final String SAMPLE = "sample";
    private static final Map<String, String> FILE_SUFFIX_TO_SYNTAX = initializeSyntaxMap();

    private static final String DOUBLE_WILDCARD_TAG = "**";

    private static final Pattern HTML_XML_SAMPLE_TAG = Pattern.compile("\\s*<!--\\s*(tag|end)::(\\S+)\\[]\\s*-->");
    private static final Pattern GENERAL_SAMPLE_TAG = Pattern.compile(".*(tag|end)::(\\S+)\\[]\\s*");

    // Map file suffixes to syntax highlighting where they differ
    private static Map<String, String> initializeSyntaxMap() {
        Map<String, String> map = new HashMap<>();
        map.put("gradle", "groovy");
        map.put("kt", "kotlin");
        map.put("kts", "kotlin");
        map.put("py", "python");
        map.put("sh", "bash");
        map.put("rb", "ruby");
        return Collections.unmodifiableMap(map);
    }

    @Override
    public boolean handles(String target) {
        return target.equals(SAMPLE);
    }

    @Override
    public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
        if (!attributes.containsKey("dir") || !attributes.containsKey("files")) {
            throw new IllegalStateException("Both the 'dir' and 'files' attributes are required to include a sample");
        }

        final String sampleBaseDir = document.getAttribute("samples-dir", ".").toString();
        final String sampleDir = attributes.get("dir").toString();
        final List<String> files = Arrays.asList(attributes.get("files").toString().split(";"));

        final String sampleContent = getSampleContent(sampleBaseDir, sampleDir, files);
        reader.pushInclude(sampleContent, target, target, 1, attributes);
    }

    private static String getSourceSyntax(String fileName) {
        String syntax = "txt";
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            String substring = fileName.substring(i + 1);
            syntax = FILE_SUFFIX_TO_SYNTAX.getOrDefault(substring, substring);
        }
        return syntax;
    }

    private static String getSampleContent(String sampleBaseDir, String sampleDir, List<String> files) {
        final StringBuilder builder = new StringBuilder(String.format("%n[.testable-sample.multi-language-sample,dir=\"%s\"]%n=====%n", sampleDir));
        for (String fileDeclaration : files) {
            final String sourceRelativeLocation = parseSourceFilePath(fileDeclaration);
            final List<String> tags = parseTags(fileDeclaration);
            final String sourceSyntax = getSourceSyntax(sourceRelativeLocation);
            String sourcePath = String.format("%s/%s/%s", sampleBaseDir, sampleDir, sourceRelativeLocation);
            String source = getContent(sourcePath);
            source = filterByTags(source, sourceSyntax, tags);
            source = trimIndent(source);
            builder.append(String.format(".%s%n[source,%s]%n----%n%s%n----%n", sourceRelativeLocation, sourceSyntax, source));
        }

        builder.append(String.format("=====%n"));
        return builder.toString();
    }

    private static String getContent(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read source file " + Paths.get(filePath).toAbsolutePath().toFile().getAbsolutePath());
        }
    }

    private static String parseSourceFilePath(String fileDeclaration) {
        return fileDeclaration.replaceAll("\\[[^]]*]", "");
    }

    private static List<String> parseTags(String fileDeclaration) {
        final List<String> tags = new ArrayList<>();
        Pattern pattern = Pattern.compile(".*\\[tags?=(.*)].*");
        Matcher matcher = pattern.matcher(fileDeclaration);
        if (matcher.matches()) {
            tags.addAll(Arrays.asList(matcher.group(1).split(",")));
        }
        return tags;
    }

    /**
     * When tags are empty or contain a single wildcard tag, the whole sample is returned (with all tag lines removed).
     *
     * @see "https://docs.asciidoctor.org/asciidoc/latest/directives/include-tagged-regions/#tag-filtering"
     */
    private static String filterByTags(String source, String syntax, List<String> tags) {
        Pattern sampleTagRegex = syntax.equals("html") || syntax.equals("xml") ? HTML_XML_SAMPLE_TAG : GENERAL_SAMPLE_TAG;

        StringBuilder result = new StringBuilder(source.length());

        boolean fullSample = tags.isEmpty() || (tags.size() == 1 && DOUBLE_WILDCARD_TAG.equals(tags.get(0)));

        if (fullSample) {
            // filter out lines matching the tagging regex
            String sampleWithoutTags = Pattern.compile("\\R").splitAsStream(source)
                .filter(line -> !sampleTagRegex.matcher(line).matches())
                .collect(Collectors.joining("\n"));
            result.append(sampleWithoutTags);
        } else {
            String activeTag = null;
            try (BufferedReader reader = new BufferedReader(new StringReader(source))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (activeTag != null) {
                        if (line.contains("end::" + activeTag + "[]")) {
                            activeTag = null;
                        } else if (!sampleTagRegex.matcher(line).matches()) {
                            result.append(line).append("\n");
                        }
                    } else {
                        activeTag = determineActiveTag(line, tags);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Unexpected exception while filtering tagged content");
            }
        }

        return result.toString();
    }

    private static String determineActiveTag(String line, List<String> tags) {
        for (String tag : tags) {
            if (line.contains("tag::" + tag + "[]")) {
                return tag;
            }
        }
        return null;
    }

    private static String trimIndent(String source) {
        String[] lines = source.split("\r\n|\r|\n");

        int minIndent = getMinIndent(lines);
        if (minIndent == 0) {
            return source;
        }

        StringBuilder sb = new StringBuilder();
        String newline = String.format("%n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                sb.append(line.substring(minIndent));
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    private static int getMinIndent(String[] lines) {
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            int indent = 0;
            while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) {
                indent++;
            }
            if (indent < line.length()) {
                minIndent = Math.min(minIndent, indent);
            }
        }
        return minIndent == Integer.MAX_VALUE ? 0 : minIndent;
    }
}
