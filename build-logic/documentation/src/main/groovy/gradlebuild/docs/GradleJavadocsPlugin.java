/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.internal.UncheckedException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generates Javadocs in a particular way.
 *
 * TODO: We should remove the workarounds here and migrate some of the changes here into the Javadoc task proper.
 */
public abstract class GradleJavadocsPlugin implements Plugin<Project> {

    @Inject
    protected abstract FileSystemOperations getFs();

    @Override
    public void apply(Project project) {
        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();

        GradleDocumentationExtension extension = project.getExtensions().getByType(GradleDocumentationExtension.class);
        generateJavadocs(project, layout, tasks, extension);
    }

    private void generateJavadocs(Project project, ProjectLayout layout, TaskContainer tasks, GradleDocumentationExtension extension) {
        Javadocs javadocs = extension.getJavadocs();
        javadocs.getJavadocCss().convention(extension.getSourceRoot().file("css/javadoc-dark-theme.css"));

        // TODO: Staging directory should be a part of the Javadocs extension
        // TODO: Pull out more of this configuration into the extension if it makes sense
        // TODO: in a typical project, this may need to be the regular javadoc task vs javadocAll

        var groovyPackageListBucket = project.getConfigurations().dependencyScope("groovyPackageListBucket");
        var groovyPackageListConf = project.getConfigurations().resolvable("groovyPackageList", conf -> {
            conf.setTransitive(false);
            conf.extendsFrom(groovyPackageListBucket.get());
        });
        project.getDependencies().add(groovyPackageListBucket.getName(), javadocs.getGroovyPackageListSrc());

        var extractGroovyPackageListTask = tasks.register("extractGroovyPackageList", Copy.class, task -> {
            task.from(project.zipTree(groovyPackageListConf.map(Configuration::getSingleFile)));
            // See https://docs.oracle.com/en/java/javase/21/docs/specs/man/javadoc.html#option-linkoffline
            task.include("package-list", "element-list");
            task.into(layout.getBuildDirectory().dir("groovyPackageList"));
        });

        TaskProvider<Javadoc> javadocAll = tasks.register("javadocAll", Javadoc.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Generate Javadocs for all API classes");

            // TODO: This breaks if version is changed later
            task.setTitle("Gradle API " + project.getVersion());

            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) task.getOptions();
            options.setEncoding("utf-8");
            options.setDocEncoding("utf-8");
            options.setCharSet("utf-8");

            options.addBooleanOption("-allow-script-in-comments", true);
            options.setHeader("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/stackoverflow-light.min.css\">" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/kotlin.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/groovy.min.js\"></script>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js\"></script>" +
                "<script>hljs.highlightAll();</script>" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-sans\" rel=\"stylesheet\">" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-serif\" rel=\"stylesheet\">" +
                "<link href=\"https://fonts.cdnfonts.com/css/dejavu-sans-mono\" rel=\"stylesheet\">"
            );

            // TODO: This would be better to model as separate options
            options.addStringOption("Xdoclint:syntax,html", "-quiet");
            // TODO: This breaks the provider
            options.addStringOption("-add-stylesheet", javadocs.getJavadocCss().get().getAsFile().getAbsolutePath());
            options.addStringOption("source", "8");
            options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:");
            // TODO: This breaks the provider
            task.getInputs().dir(javadocs.getJavaPackageListLoc());
            var javaApiLink = javadocs.getJavaApi().map(URI::toString).map(v -> {
                if (v.endsWith("/")) {
                    return v.substring(0, v.length() - 1);
                }
                return v;
            }).get();
            options.linksOffline(javaApiLink, javadocs.getJavaPackageListLoc().map(Directory::getAsFile).get().getAbsolutePath());
            // TODO: This breaks the provider
            task.getInputs().dir(extractGroovyPackageListTask.map(Copy::getDestinationDir)).withPathSensitivity(PathSensitivity.NONE);
            options.linksOffline(javadocs.getGroovyApi().get().toString(), extractGroovyPackageListTask.map(Copy::getDestinationDir).get().getAbsolutePath());

            task.source(extension.getDocumentedSource()
                .filter(f -> f.getName().endsWith(".java"))
                .filter(new DeduplicatePackageInfoFiles())
            );

            task.setClasspath(extension.getClasspath());

            // TODO: This should be in Javadoc task
            DirectoryProperty generatedJavadocDirectory = project.getObjects().directoryProperty();
            generatedJavadocDirectory.set(layout.getBuildDirectory().dir("javadoc"));
            task.getOutputs().dir(generatedJavadocDirectory);
            task.getExtensions().getExtraProperties().set("destinationDirectory", generatedJavadocDirectory);
            // TODO: This breaks the provider
            task.setDestinationDir(generatedJavadocDirectory.get().getAsFile());
        });

        // TODO: destinationDirectory should be part of Javadoc
        javadocs.getRenderedDocumentation().from(javadocAll.flatMap(task -> (DirectoryProperty) task.getExtensions().getExtraProperties().get("destinationDirectory")));

        CheckstyleExtension checkstyle = project.getExtensions().getByType(CheckstyleExtension.class);
        tasks.register("checkstyleApi", Checkstyle.class, task -> {
            task.source(extension.getDocumentedSource());
            // TODO: This is ugly
            task.setConfig(project.getResources().getText().fromFile(checkstyle.getConfigDirectory().file("checkstyle-api.xml")));
            task.setClasspath(layout.files());
            task.getReports().getXml().getOutputLocation().set(new File(checkstyle.getReportsDir(), "checkstyle-api.xml"));
        });
    }

    private static class DeduplicatePackageInfoFiles implements Spec<File> {

        private final Pattern pattern = Pattern.compile("package\\s*([^;\\s]+)\\s*;");

        private final Set<String> packagesSeenBefore = new HashSet<>();
        private final Set<File> canonicalPackageInfos = new HashSet<>();

        @Override
        public boolean isSatisfiedBy(File file) {
            try {
                if (file.getName().equals("package-info.java")) {
                    if (canonicalPackageInfos.contains(file.getAbsoluteFile())) {
                        // The file collection may be resolved several times, e.g. for fingerprinting and for actual javadoc invocation.
                        // The method should be idempotent, so we record all package-info.java files we ever allowed and allow them afterward.
                        return true;
                    }
                    String packageName = getPackageName(file);
                    // we pass through package-info.java files for packages we have not seen before, block the rest
                    boolean notSeeBefore = packagesSeenBefore.add(packageName);
                    if (notSeeBefore) {
                        canonicalPackageInfos.add(file.getAbsoluteFile());
                    }
                    return notSeeBefore;
                } else {
                    return true; // not a package-info.java file, we ignore it
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e, true);
            }
        }

        private String getPackageName(File file) throws IOException {
            try (Stream<String> lines = Files.lines(file.toPath())) {
                String packageLine = lines.filter(line -> line.startsWith("package"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Can't find package definition in file " + file));
                Matcher matcher = pattern.matcher(packageLine);
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    throw new IOException("Can't extract package name from file " + file);
                }
            }
        }
    }
}
