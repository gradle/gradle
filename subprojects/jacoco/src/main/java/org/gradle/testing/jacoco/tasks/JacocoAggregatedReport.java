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
package org.gradle.testing.jacoco.tasks;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.jacoco.AntJacocoReport;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;

/**
 * Task to aggregate HTML, XML and CSV reports of Jacoco coverage data from multiple projects and/or multiple Test tasks.
 *
 * @since 7.2
 */
@Incubating
@CacheableTask
public abstract class JacocoAggregatedReport extends JacocoReport {

    public static final String AGGREGATION_CONFIGURATION_NAME = "jacocoAggregation";

    /**
     * Configures the test categories to be aggregated by this task.
     * The tests category is the name of the test task.
     *
     * Defaults to ["test"].
     */
    @Input
    public abstract ListProperty<String> getTestCategories();

    @Inject
    public JacocoAggregatedReport(JvmEcosystemUtilities jvmEcosystemUtilities) {
        getTestCategories().convention(Collections.singletonList("test"));

        Project project = getProject();
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            // TODO: what about other "production" source sets that the users might add?
            sourceSets(javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME));

            executionData(getTestCategories().map(categories -> {
                ConfigurableFileCollection coverageFiles = project.files();
                for (String testCategory : categories) {
                    TaskContainer tasks = project.getTasks();
                    if (tasks.findByName(testCategory) != null) {
                        coverageFiles.from(project.getTasks().named(testCategory).map(task -> task.getExtensions().getByType(JacocoTaskExtension.class).getDestinationFile()));
                    }
                }
                return coverageFiles;
            }));
        });

        Configuration aggregationConfiguration = project.getConfigurations().findByName(AGGREGATION_CONFIGURATION_NAME);
        resolveClassesVariantsFrom(aggregationConfiguration, jvmEcosystemUtilities);
        resolveSourcesVariantsFrom(aggregationConfiguration);
        resolveTestCoverageDataVariantsFrom(aggregationConfiguration);
    }

    private void resolveClassesVariantsFrom(Configuration aggregationConfiguration, JvmEcosystemUtilities jvmEcosystemUtilities) {
        Configuration coverageClassesDirs = createResolver("coverageClassesDirs");
        coverageClassesDirs.extendsFrom(aggregationConfiguration);
        coverageClassesDirs.getResolutionStrategy().getComponentSelection().all(s -> s.reject("external dependencies are excluded from code coverage report"));
        jvmEcosystemUtilities.configureAsRuntimeClasspath(coverageClassesDirs);
        additionalClassDirs(coverageClassesDirs.getIncoming().artifactView(it -> it.lenient(true)).getFiles());
    }

    private void resolveSourcesVariantsFrom(Configuration aggregationConfiguration) {
        Configuration sourcesPath = createResolver("sourcesPath");
        sourcesPath.extendsFrom(aggregationConfiguration);
        sourcesPath.attributes(sourceDirectoriesAttributes(getProject()));
        additionalSourceDirs(sourcesPath.getIncoming().artifactView(it -> it.lenient(true)).getFiles());
    }

    private void resolveTestCoverageDataVariantsFrom(Configuration aggregationConfiguration) {
        Project project = getProject();
        executionData(getTestCategories().map(categories -> {
            ConfigurableFileCollection coverageFiles = project.files();
            for (String testCategory : categories) {
                String resolverName = testCategory + "CoverageDataPath";
                Configuration coverageDataPath = project.getConfigurations().findByName(resolverName);
                if (coverageDataPath == null) {
                    coverageDataPath = createResolver(resolverName);
                    coverageDataPath.extendsFrom(aggregationConfiguration);
                    coverageDataPath.attributes(coverageDataAttributes(project, testCategory));
                }
                coverageFiles.from(coverageDataPath.getIncoming().artifactView(it -> it.lenient(true)).getFiles());
            }
            return coverageFiles;
        }));
    }

    @Override
    public void generate() {
        new AntJacocoReport(getAntBuilder()).execute(
            getJacocoClasspath(),
            getReportProjectName().get(),
            getAllClassDirs().filter(File::exists),
            getAllSourceDirs().filter(File::exists),
            getExecutionData().filter(File::exists),
            getReports()
        );
    }

    private Configuration createResolver(String name) {
        ConfigurationContainer configurations = getProject().getConfigurations();
        return configurations.create(name, c -> {
            c.setVisible(false);
            c.setCanBeResolved(true);
            c.setCanBeConsumed(false);
        });
    }

    public static Action<? super AttributeContainer> sourceDirectoriesAttributes(Project project) {
        return a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, "source-directories"));
        };
    }

    public static Action<? super AttributeContainer> coverageDataAttributes(Project project, String testCategory) {
        return a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, "jacoco-coverage-data"));
            a.attribute(TestCategory.ATTRIBUTE, project.getObjects().named(TestCategory.class, testCategory));
        };
    }

    private interface TestCategory extends Named {
        Attribute<TestCategory> ATTRIBUTE = Attribute.of("org.gradle.test-category", TestCategory.class);
    }
}
