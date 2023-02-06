/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public abstract class PluginAnalysisAggregatorTask extends DefaultTask {

    @InputFiles
    abstract ConfigurableFileCollection getAnalysisJson();

    @OutputFile
    abstract RegularFileProperty getOutputFile();

    @OutputFile
    abstract RegularFileProperty getStylesheetFile();

    public PluginAnalysisAggregatorTask() {
        getOutputFile().convention(getProject().getLayout().getBuildDirectory().file("plugin-analysis/report.html"));
        getStylesheetFile().convention(getProject().getLayout().getBuildDirectory().file("plugin-analysis/plugin-analysis-report.css"));
    }

    @TaskAction
    public void execute() {

        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.readerFor(
            GradleApiUsageCollectorTask.PluginGradleApiUsageResults.class);

        // Track which components are used by which plugins.
        // Currently, this is sorta inefficient since if multiple plugins are implemented
        // by the same component, we analyze that component multiple times.
        Map<String, Set<String>> componentsToPlugins = new TreeMap<>();
        Map<String, Map<String, Set<String>>> apiUsage = new TreeMap<>();
        for (File f : getAnalysisJson()) {
            GradleApiUsageCollectorTask.PluginGradleApiUsageResults results = readJsonAnalysis(reader, f);
            results.getComponents().forEach(component -> {
                componentsToPlugins.computeIfAbsent(component.getComponentId(),
                    id -> new TreeSet<>()).add(results.getPluginName());

                component.getClasses().forEach(clazz -> {
                    clazz.getDependencyClasses().forEach(gradleClass -> {
                        Map<String, Set<String>> mapForGradleClass =
                            apiUsage.computeIfAbsent(gradleClass, name -> new TreeMap<>());

                        Set<String> componentClasses = mapForGradleClass.computeIfAbsent(component.getComponentId(), id -> new TreeSet<>());
                        componentClasses.add(clazz.getName());
                    });
                });
            });
        }

        try (InputStream is = getClass().getResourceAsStream("/gradlebuild/plugin-analysis-report.css")) {
            is.transferTo(Files.newOutputStream(getStylesheetFile().getAsFile().get().toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (PrintStream html = new PrintStream(new FileOutputStream(getOutputFile().getAsFile().get()))) {
            html.println("<html>");
            html.println("<head>");
            html.println("<link rel=\"stylesheet\" href=\"plugin-analysis-report.css\">");
            html.println("</head>");

            html.println("<body>");

            html.println("<h1>Gradle Plugin API Usage Report</h1>");
            html.println("<details>");
            html.println("<summary>Analyzed Plugins</summary>");
            html.println("<ul>");
            componentsToPlugins.values().stream().flatMap(x -> x.stream()).distinct().sorted().forEach(plugin -> {
                html.print("<li>");
                html.print(plugin);
                html.println("</li>");
            });
            html.println("</ul>");
            html.println("</details>");

            html.println("<h2>API Usage by Plugin</h2>");

            html.println("<ul>");
            apiUsage.entrySet().stream().sorted(Comparator.comparingInt(x -> -1 * x.getValue().size())).forEach(entry -> {

                String gradleClass = entry.getKey();

                List<String> cssClasses = new ArrayList<>();
                cssClasses.add("gradle-class");
                if (gradleClass.contains("internal")) {
                    cssClasses.add("gradle-internal-class");
                } else {
                    cssClasses.add("gradle-api-class");
                }

                html.println("<li class=\"" + String.join(" ", cssClasses) + "\"><details>");
                html.print("<summary><span>");
                html.print(gradleClass + " (" + entry.getValue().size() + ")");
                html.println("</span></summary>");

                html.println("<ul>");
                entry.getValue().entrySet().stream().sorted(Comparator.comparingInt(x -> -1 * x.getValue().size())).forEach(componentEntry -> {

                    Set<String> plugins = componentsToPlugins.get(componentEntry.getKey());
                    String name = plugins.size() == 1 ? plugins.iterator().next() : componentEntry.getKey();

                    html.println("<li class=\"plugin-component\"><details>");
                    html.print("<summary><span>");
                    html.print(name + " (" + componentEntry.getValue().size() + ")");
                    html.println("</span></summary>");

                    html.println("<ul>");
                    componentEntry.getValue().stream().forEach(clazz -> {
                        html.print("<li>");
                        html.print(clazz);
                        html.println("</li>");
                    });
                    html.println("</ul>");
                    html.println("</details></li>");


                });
                html.println("</ul>");
                html.println("</details></li>");
            });
            html.println("</ul>");

            html.println("</body></html>");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Plugin API Usage Report: " + getOutputFile().get().getAsFile().toURI());
    }

    private static GradleApiUsageCollectorTask.PluginGradleApiUsageResults readJsonAnalysis(ObjectReader reader, File f) {
        try {
            return reader.readValue(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
