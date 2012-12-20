/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultGroovySourceSet;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.util.VersionNumber;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends {@link org.gradle.api.plugins.JavaBasePlugin} to provide support for compiling and documenting Groovy
 * source files.
 *
 * @author Hans Dockter
 */
public class GroovyBasePlugin implements Plugin<ProjectInternal> {
    public static final String GROOVY_CONFIGURATION_NAME = "groovy";
    private static final Pattern GROOVY_JAR_PATTERN = Pattern.compile("(groovy(?:-all)?)-(\\d.*?)(-indy)?.jar");

    private final FileResolver fileResolver;
    private ProjectInternal project;

    @Inject
    public GroovyBasePlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public void apply(ProjectInternal project) {
        this.project = project;
        JavaBasePlugin javaBasePlugin = project.getPlugins().apply(JavaBasePlugin.class);

        project.getConfigurations().add(GROOVY_CONFIGURATION_NAME).setVisible(false).
                setDescription("The Groovy libraries to be used for this Groovy project.");

        configureCompileDefaults();
        configureSourceSetDefaults(javaBasePlugin);

        configureGroovydoc();
    }

    private void configureCompileDefaults() {
        project.getTasks().withType(GroovyCompile.class, new Action<GroovyCompile>() {
            public void execute(final GroovyCompile compile) {                                                                                                                                                                                                                                            compile.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getGroovyClasspath(compile.getClasspath());
                    }
                });
            }
        });
    }

    private void configureSourceSetDefaults(final JavaBasePlugin javaBasePlugin) {
        project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().all(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                final DefaultGroovySourceSet groovySourceSet = new DefaultGroovySourceSet(((DefaultSourceSet) sourceSet).getDisplayName(), fileResolver);
                new DslObject(sourceSet).getConvention().getPlugins().put("groovy", groovySourceSet);

                groovySourceSet.getGroovy().srcDir(String.format("src/%s/groovy", sourceSet.getName()));
                sourceSet.getResources().getFilter().exclude(new Spec<FileTreeElement>() {
                    public boolean isSatisfiedBy(FileTreeElement element) {
                        return groovySourceSet.getGroovy().contains(element.getFile());
                    }
                });
                sourceSet.getAllJava().source(groovySourceSet.getGroovy());
                sourceSet.getAllSource().source(groovySourceSet.getGroovy());

                String compileTaskName = sourceSet.getCompileTaskName("groovy");
                GroovyCompile compile = project.getTasks().add(compileTaskName, GroovyCompile.class);
                javaBasePlugin.configureForSourceSet(sourceSet, compile);
                compile.dependsOn(sourceSet.getCompileJavaTaskName());
                compile.setDescription(String.format("Compiles the %s Groovy source.", sourceSet.getName()));
                compile.setSource(groovySourceSet.getGroovy());

                project.getTasks().getByName(sourceSet.getClassesTaskName()).dependsOn(compileTaskName);
            }
        });
    }

    private void configureGroovydoc() {
        project.getTasks().withType(Groovydoc.class, new Action<Groovydoc>() {
            public void execute(final Groovydoc groovydoc) {
                groovydoc.getConventionMapping().map("groovyClasspath", new Callable<Object>() {
                    public Object call() throws Exception {
                        return getGroovyClasspath(groovydoc.getClasspath());
                    }
                });
                groovydoc.getConventionMapping().map("destinationDir", new Callable<Object>() {
                    public Object call() throws Exception {
                        return new File(java(project.getConvention()).getDocsDir(), "groovydoc");
                    }
                });
                groovydoc.getConventionMapping().map("docTitle", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
                groovydoc.getConventionMapping().map("windowTitle", new Callable<Object>() {
                    public Object call() throws Exception {
                        return project.getExtensions().getByType(ReportingExtension.class).getApiDocTitle();
                    }
                });
            }
        });
    }

    private FileCollection getGroovyClasspath(FileCollection classpath) {
        Configuration groovyClasspath = project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME);
        if (groovyClasspath.getDependencies().isEmpty() && !project.getRepositories().isEmpty()) {
            Matcher groovyJar = findGroovyJar(classpath);
            if (groovyJar != null) {
                // project.getDependencies().create(String) seems to be the only feasible way to create a Dependency with a classifier
                String notation = "org.codehaus.groovy:" + groovyJar.group(1) + ":" + groovyJar.group(2);
                if (groovyJar.group(3) != null) {
                    notation += ":indy";
                }
                List<Dependency> dependencies = Lists.newArrayList();
                dependencies.add(project.getDependencies().create(notation));
                if (groovyJar.group(1).equals("groovy") && VersionNumber.parse(groovyJar.group(2)).getMajor() >= 2) {
                    // for when AntGroovyCompiler is used
                    dependencies.add(project.getDependencies().create(notation.replace(":groovy:", ":groovy-ant:")));
                }
                groovyClasspath = project.getConfigurations().detachedConfiguration(dependencies.toArray(new Dependency[dependencies.size()]));
            }
        }
        return groovyClasspath;
    }

    private JavaPluginConvention java(Convention convention) {
        return convention.getPlugin(JavaPluginConvention.class);
    }

    private Matcher findGroovyJar(Iterable<File> classpath) {
        if (classpath == null) { return null; }
        for (File file : classpath) {
            Matcher matcher = GROOVY_JAR_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                return matcher;
            }
        }
        return null;
    }
}