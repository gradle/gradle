/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import com.google.common.util.concurrent.Callables;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A plugin for the <a href="http://pmd.sourceforge.net/">PMD</a> source code analyzer.
 * <p>
 * Declares a <tt>pmd</tt> configuration which needs to be configured with the PMD library to be used.
 * <p>
 * For each source set that is to be analyzed, a {@link Pmd} task is created and configured to analyze all Java code.
 * <p>
 * All PMD tasks (including user-defined ones) are added to the <tt>check</tt> lifecycle task.
 *
 * @see PmdExtension
 * @see Pmd
 */
public class PmdPlugin extends AbstractCodeQualityPlugin<Pmd> {

    public static final String DEFAULT_PMD_VERSION = "5.6.1";
    private PmdExtension extension;

    @Override
    protected String getToolName() {
        return "PMD";
    }

    @Override
    protected Class<Pmd> getTaskType() {
        return Pmd.class;
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("pmd", PmdExtension.class, project);
        extension.setToolVersion(DEFAULT_PMD_VERSION);
        extension.setRuleSets(new ArrayList<String>(Arrays.asList("java-basic")));
        extension.setRuleSetFiles(project.files());
        conventionMappingOf(extension).map("targetJdk", new Callable<Object>() {
            @Override
            public Object call() {
                return getDefaultTargetJdk(getJavaPluginConvention().getSourceCompatibility());
            }
        });
        return extension;
    }

    public TargetJdk getDefaultTargetJdk(JavaVersion javaVersion) {
        try {
            return TargetJdk.toVersion(javaVersion.toString());
        } catch (IllegalArgumentException ignored) {
            // TargetJDK does not include 1.1, 1.2 and 1.8;
            // Use same fallback as PMD
            return TargetJdk.VERSION_1_4;
        }
    }

    @Override
    protected void configureTaskDefaults(Pmd task, String baseName) {
        Configuration configuration = project.getConfigurations().getAt("pmd");
        configureDefaultDependencies(configuration);
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                VersionNumber version = VersionNumber.parse(extension.getToolVersion());
                String dependency = calculateDefaultDependencyNotation(version);
                dependencies.add(project.getDependencies().create(dependency));
            }
        });
    }

    private void configureTaskConventionMapping(Configuration configuration, Pmd task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("pmdClasspath", Callables.returning(configuration));
        taskMapping.map("ruleSets", new Callable<List<String>>() {
            @Override
            public List<String> call() {
                return extension.getRuleSets();
            }
        });
        taskMapping.map("ruleSetConfig", new Callable<TextResource>() {
            @Override
            public TextResource call() {
                return extension.getRuleSetConfig();
            }
        });
        taskMapping.map("ruleSetFiles", new Callable<FileCollection>() {
            @Override
            public FileCollection call() {
                return extension.getRuleSetFiles();
            }
        });
        taskMapping.map("ignoreFailures", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return extension.isIgnoreFailures();
            }
        });
        taskMapping.map("rulePriority", new Callable<Integer>() {
            @Override
            public Integer call() {
                return extension.getRulePriority();
            }
        });
        taskMapping.map("consoleOutput", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return extension.isConsoleOutput();
            }
        });
        taskMapping.map("targetJdk", new Callable<TargetJdk>() {
            @Override
            public TargetJdk call() {
                return extension.getTargetJdk();
            }
        });
    }

    private void configureReportsConventionMapping(Pmd task, final String baseName) {
        task.getReports().all(new Action<SingleFileReport>() {
            @Override
            public void execute(final SingleFileReport report) {
                ConventionMapping reportMapping = AbstractCodeQualityPlugin.conventionMappingOf(report);
                reportMapping.map("enabled", Callables.returning(true));
                reportMapping.map("destination", new Callable<File>() {
                    @Override
                    public File call() {
                        return new File(extension.getReportsDir(), baseName + "." + report.getName());
                    }
                });
            }
        });
    }

    private String calculateDefaultDependencyNotation(VersionNumber toolVersion) {
        if (toolVersion.compareTo(VersionNumber.version(5)) < 0) {
            return "pmd:pmd:" + extension.getToolVersion();
        } else if (toolVersion.compareTo(VersionNumber.parse("5.2.0")) < 0) {
            return "net.sourceforge.pmd:pmd:" + extension.getToolVersion();
        }
        return "net.sourceforge.pmd:pmd-java:" + extension.getToolVersion();
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, final Pmd task) {
        task.setDescription("Run PMD analysis for " + sourceSet.getName() + " classes");
        task.setSource(sourceSet.getAllJava());
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("classpath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {
                return sourceSet.getOutput().plus(sourceSet.getCompileClasspath());
            }
        });
    }
}
