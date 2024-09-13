/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.binarycompatibility;

import gradlebuild.basics.Gradle9PropertyUpgradeSupport;
import japicmp.filter.Filter;
import me.champeau.gradle.japicmp.JApiCmpWorkAction;
import me.champeau.gradle.japicmp.JApiCmpWorkerAction;
import me.champeau.gradle.japicmp.JapiCmpWorkerConfiguration;
import me.champeau.gradle.japicmp.filters.FilterConfiguration;
import me.champeau.gradle.japicmp.report.RichReport;
import me.champeau.gradle.japicmp.report.RuleConfiguration;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CacheableTask
public abstract class JapicmpTask extends DefaultTask {

    private final ConfigurableFileCollection additionalJapicmpClasspath;

    public JapicmpTask() {
        getFailOnModification().convention(false);
        getFailOnSourceIncompatibility().convention(false);
        getIgnoreMissingClasses().convention(false);
        getIncludeSynthetic().convention(false);
        getOnlyBinaryIncompatibleModified().convention(false);
        getOnlyModified().convention(false);
        getAccessModifier().convention("public");
        ConfigurableFileCollection classpath = getProject().getObjects().fileCollection();
        if (JavaVersion.current().isJava9Compatible()) {
            classpath.from(resolveJaxb());
        }
        if (GradleVersion.current().compareTo(GradleVersion.version("6.0")) >= 0) {
            classpath.from(resolveGuava());
        }
        additionalJapicmpClasspath = classpath;
    }

    @TaskAction
    public void exec() {
        ConfigurableFileCollection oldArchives = getOldArchives();
        ConfigurableFileCollection newArchives = getNewArchives();
        List<JApiCmpWorkerAction.Archive> baseline = !oldArchives.isEmpty() ? toArchives(oldArchives) : inferArchives(getOldClasspath());
        List<JApiCmpWorkerAction.Archive> current = !newArchives.isEmpty() ? toArchives(newArchives) : inferArchives(getNewClasspath());
        execForNewGradle(baseline, current);
    }

    private void execForNewGradle(final List<JApiCmpWorkerAction.Archive> baseline, final List<JApiCmpWorkerAction.Archive> current) {
        WorkQueue queue = getWorkerExecutor().processIsolation(spec -> {
            spec.getClasspath().from(calculateWorkerClasspath());
            Gradle9PropertyUpgradeSupport.setProperty(spec.getForkOptions(), "setMaxHeapSize", "1g");
        });
        queue.submit(JApiCmpWorkAction.class, params -> params.getConfiguration().set(calculateWorkerConfiguration(baseline, current)));
    }

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    private Set<File> calculateWorkerClasspath() {
        Set<File> classpath = new HashSet<>();
        for (FilterConfiguration configuration : getIncludeFilters().getOrElse(Collections.emptyList())) {
            addClasspathFor(configuration.getFilterClass(), classpath);
        }
        for (FilterConfiguration configuration : getExcludeFilters().getOrElse(Collections.emptyList())) {
            addClasspathFor(configuration.getFilterClass(), classpath);
        }
        if (getRichReport().isPresent()) {
            RichReport richReport = getRichReport().get();
            for (RuleConfiguration configuration : richReport.getRules().getOrElse(Collections.emptyList())) {
                addClasspathFor(configuration.getRuleClass(), classpath);
            }
        }
        classpath.addAll(additionalJapicmpClasspath.getFiles());
        return classpath;
    }

    private JapiCmpWorkerConfiguration calculateWorkerConfiguration(List<JApiCmpWorkerAction.Archive> baseline, List<JApiCmpWorkerAction.Archive> current) {
        return new JapiCmpWorkerConfiguration(
                getIncludeSynthetic().get(),
                getIgnoreMissingClasses().get(),
                getPackageIncludes().getOrElse(Collections.emptyList()),
                getPackageExcludes().getOrElse(Collections.emptyList()),
                getClassIncludes().getOrElse(Collections.emptyList()),
                getClassExcludes().getOrElse(Collections.emptyList()),
                getMethodIncludes().getOrElse(Collections.emptyList()),
                getMethodExcludes().getOrElse(Collections.emptyList()),
                getFieldIncludes().getOrElse(Collections.emptyList()),
                getFieldExcludes().getOrElse(Collections.emptyList()),
                getAnnotationIncludes().getOrElse(Collections.emptyList()),
                getAnnotationExcludes().getOrElse(Collections.emptyList()),
                getIncludeFilters().getOrElse(Collections.emptyList()),
                getExcludeFilters().getOrElse(Collections.emptyList()),
                getCompatibilityChangeExcludes().getOrElse(Collections.emptyList()),
                toArchives(getOldClasspath()),
                toArchives(getNewClasspath()),
                baseline,
                current,
                getOnlyModified().get(),
                getOnlyBinaryIncompatibleModified().get(),
                getFailOnSourceIncompatibility().get(),
                getAccessModifier().get(),
                maybeFile(getXmlOutputFile()),
                maybeFile(getHtmlOutputFile()),
                maybeFile(getTxtOutputFile()),
                getFailOnModification().get(),
                reportConfigurationOf(getRichReport())
        );
    }

    private static RichReport.Configuration reportConfigurationOf(Provider<RichReport> report) {
        if (report.isPresent()) {
            return report.get().toConfiguration();
        }
        return null;
    }

    private static File maybeFile(RegularFileProperty property) {
        if (property.isPresent()) {
            return property.getAsFile().get();
        }
        return null;
    }

    private Configuration resolveJaxb() {
        Project project = getProject();
        DependencyHandler dependencies = project.getDependencies();
        return project.getConfigurations().detachedConfiguration(
                dependencies.create("javax.xml.bind:jaxb-api:2.3.0"),
                dependencies.create("com.sun.xml.bind:jaxb-core:2.3.0.1"),
                dependencies.create("com.sun.xml.bind:jaxb-impl:2.3.0.1"),
                dependencies.create("javax.activation:activation:1.1.1")
        );
    }

    private Configuration resolveGuava() {
        Project project = getProject();
        DependencyHandler dependencies = project.getDependencies();
        return project.getConfigurations().detachedConfiguration(
                dependencies.create("com.google.guava:guava:30.1.1-jre")
        );
    }

    private void addClasspathFor(Class<?> clazz, Set<File> classpath) {
        ProtectionDomain domain = clazz.getProtectionDomain();
        CodeSource codeSource = domain.getCodeSource();
        if (codeSource != null) {
            try {
                classpath.add(new File(codeSource.getLocation().toURI()));
            } catch (URISyntaxException e) {
                // silent
            }
        }
    }

    private List<JApiCmpWorkerAction.Archive> inferArchives(FileCollection fc) {
        if (fc instanceof Configuration) {
            final List<JApiCmpWorkerAction.Archive> archives = new ArrayList<>();
            Set<ResolvedDependency> firstLevelModuleDependencies = ((Configuration) fc).getResolvedConfiguration().getFirstLevelModuleDependencies();
            for (ResolvedDependency moduleDependency : firstLevelModuleDependencies) {
                collectArchives(archives, moduleDependency);
            }
            return archives;
        }

        return toArchives(fc);
    }

    private static List<JApiCmpWorkerAction.Archive> toArchives(FileCollection fc) {
        Set<File> files = fc.getFiles();
        List<JApiCmpWorkerAction.Archive> archives = new ArrayList<>(files.size());
        for (File file : files) {
            archives.add(new JApiCmpWorkerAction.Archive(file, "1.0"));
        }
        return archives;
    }

    private void collectArchives(final List<JApiCmpWorkerAction.Archive> archives, ResolvedDependency resolvedDependency) {
        String version = resolvedDependency.getModule().getId().getVersion();
        Set<ResolvedArtifact> allModuleArtifacts = resolvedDependency.getAllModuleArtifacts();
        for (ResolvedArtifact resolvedArtifact : allModuleArtifacts) {
            archives.add(new JApiCmpWorkerAction.Archive(resolvedArtifact.getFile(), version));
        }
        for (ResolvedDependency dependency : resolvedDependency.getChildren()) {
            collectArchives(archives, dependency);
        }
    }

    public void richReport(Action<? super RichReport> configureAction) {
        if (!getRichReport().isPresent()) {
            RichReport richReport = getProject().getObjects().newInstance(RichReport.class);
            DirectoryProperty buildDirectory = getProject().getLayout().getBuildDirectory();
            richReport.getDestinationDir().convention(buildDirectory.dir("reports"));
            getRichReport().set(richReport);
        }

        configureAction.execute(getRichReport().get());
    }

    @Input
    @Optional
    public abstract ListProperty<String> getPackageIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getPackageExcludes();

    @Input
    @Optional
    public abstract ListProperty<String> getClassIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getClassExcludes();

    @Input
    @Optional
    public abstract ListProperty<String> getMethodIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getMethodExcludes();

    @Input
    @Optional
    public abstract ListProperty<String> getFieldIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getFieldExcludes();

    @Input
    @Optional
    public abstract ListProperty<String> getAnnotationIncludes();

    @Input
    @Optional
    public abstract ListProperty<String> getAnnotationExcludes();

    @Input
    @Optional
    public abstract ListProperty<FilterConfiguration> getIncludeFilters();

    public void addIncludeFilter(Class<? extends Filter> includeFilterClass) {
        getIncludeFilters().add(new FilterConfiguration(includeFilterClass));
    }

    @Input
    @Optional
    public abstract ListProperty<FilterConfiguration> getExcludeFilters();

    public void addExcludeFilter(Class<? extends Filter> excludeFilterClass) {
        getExcludeFilters().add(new FilterConfiguration(excludeFilterClass));
    }

    @Input
    @Optional
    public abstract ListProperty<String> getCompatibilityChangeExcludes();

    @Input
    @Optional
    public abstract Property<String> getAccessModifier();

    @Input
    public abstract Property<Boolean> getOnlyModified();

    @Input
    public abstract Property<Boolean> getOnlyBinaryIncompatibleModified();

    @Input
    public abstract Property<Boolean> getFailOnSourceIncompatibility();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getXmlOutputFile();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getHtmlOutputFile();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getTxtOutputFile();

    @Input
    public abstract Property<Boolean> getFailOnModification();

    @Input
    public abstract Property<Boolean> getIncludeSynthetic();

    @CompileClasspath
    public abstract ConfigurableFileCollection getOldClasspath();

    @CompileClasspath
    public abstract ConfigurableFileCollection getNewClasspath();

    @Optional
    @CompileClasspath
    public abstract ConfigurableFileCollection getOldArchives();

    @Optional
    @CompileClasspath
    public abstract ConfigurableFileCollection getNewArchives();

    @Input
    public abstract Property<Boolean> getIgnoreMissingClasses();

    @Optional
    @Nested
    public abstract Property<RichReport> getRichReport();

}
