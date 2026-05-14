/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class GeneratorTask extends DefaultTask {

    private static final String MARKER_COMMENT = "<!-- This diagram is generated. Use `./gradlew :architectureDoc` to update it -->";
    private static final String START_DIAGRAM = "```mermaid";
    private static final String END_DIAGRAM = "```";

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract ListProperty<ArchitectureElement> getElements();

    @TaskAction
    public void generate() {
        File markdownFile = getOutputFile().getAsFile().get();
        List<String> head;
        if (markdownFile.exists()) {
            List<String> content;
            try {
                content = Files.readAllLines(markdownFile.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            int markerPos = indexOfFirst(content, line -> line.contains(MARKER_COMMENT));
            if (markerPos < 0) {
                throw new IllegalArgumentException("Could not locate the generated diagram in " + markdownFile);
            }
            int endPos = indexOfFirst(content.subList(markerPos, content.size()),
                line -> line.contains(END_DIAGRAM) && !line.contains(START_DIAGRAM));
            if (endPos < 0) {
                throw new IllegalArgumentException("Could not locate the end of the generated diagram in " + markdownFile);
            }
            head = content.subList(0, markerPos);
        } else {
            head = Collections.emptyList();
        }

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(markdownFile.toPath());
             PrintWriter writer = new PrintWriter(bufferedWriter)) {
            for (String line : head) {
                writer.println(line);
            }
            graph(writer, getElements().get());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void graph(PrintWriter writer, List<ArchitectureElement> elements) {
        writer.println(MARKER_COMMENT);
        writer.println(START_DIAGRAM);
        NodeWriter nodeWriter = new NodeWriter(writer, "    ");
        nodeWriter.node("graph TD");
        for (ArchitectureElement element : elements) {
            if (element instanceof Platform) {
                platform(nodeWriter, (Platform) element);
            } else {
                element(nodeWriter, element);
            }
        }
        writer.println(END_DIAGRAM);
    }

    private static void platform(NodeWriter writer, Platform platform) {
        writer.newLine();
        writer.node("subgraph " + platform.getId() + "[\"" + platform.getName() + " platform\"]", child -> {
            for (ArchitectureModule module : platform.getChildren()) {
                element(child, module);
            }
        });
        writer.node("end");
        writer.node("style " + platform.getId() + " fill:#c2e0f4,stroke:#3498db,stroke-width:2px,color:#000;");
        for (ElementId dep : platform.getUses()) {
            writer.node(platform.getId() + " --> " + dep);
        }
    }

    private static void element(NodeWriter writer, ArchitectureElement element) {
        writer.newLine();
        writer.node(element.getId() + "[\"" + element.getName() + " module\"]");
        writer.node("style " + element.getId() + " stroke:#1abc9c,fill:#b1f4e7,stroke-width:2px,color:#000;");
    }

    private static int indexOfFirst(List<String> lines, Predicate<String> predicate) {
        for (int i = 0; i < lines.size(); i++) {
            if (predicate.test(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static final class NodeWriter {

        private final PrintWriter writer;
        private final String indent;

        NodeWriter(PrintWriter writer, String indent) {
            this.writer = writer;
            this.indent = indent;
        }

        void newLine() {
            writer.println();
        }

        void node(String node) {
            writer.print(indent);
            writer.println(node);
        }

        void node(String node, Consumer<NodeWriter> builder) {
            writer.print(indent);
            writer.println(node);
            builder.accept(new NodeWriter(writer, indent + "    "));
        }
    }
}
