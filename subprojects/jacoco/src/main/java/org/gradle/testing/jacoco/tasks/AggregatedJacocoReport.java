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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.internal.jacoco.AntJacocoReport;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

import javax.inject.Inject;
import java.io.File;

/**
 * Task to aggregate HTML, Xml and CSV reports of Jacoco coverage data.
 *
 * @since 7.2
 */
@Incubating
@CacheableTask
public class AggregatedJacocoReport extends JacocoReport {

    public static final String AGGREGATION_CONFIGURATION_NAME = "jacocoAggregation";

    @Inject
    public AggregatedJacocoReport(JvmEcosystemUtilities jvmEcosystemUtilities) {
        Project project = getProject();
        project.getPluginManager().withPlugin("java", plugin -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            // TODO: what about other "production" source sets that the users might add?
            sourceSets(javaPluginExtension.getSourceSets().getByName("main"));
            executionData(project.getTasks().named("test").map(task -> task.getExtensions().getByType(JacocoTaskExtension.class).getDestinationFile()));
        });

        ConfigurationContainer configurations = project.getConfigurations();
        Configuration jacocoAggregation = configurations.getByName(AGGREGATION_CONFIGURATION_NAME);

        Configuration coverageClassesDirs = createResolver("coverageClassesDirs");
        coverageClassesDirs.extendsFrom(jacocoAggregation);
        jvmEcosystemUtilities.configureAsRuntimeClasspath(coverageClassesDirs);
        // TODO: is there a better way to not include external dependency classes?
        additionalClassDirs(coverageClassesDirs.filter(it -> it.getPath().contains(File.separator + "build" + File.separator + "libs" + File.separator)));

        Configuration sourcesPath = createResolver("sourcesPath");
        sourcesPath.extendsFrom(jacocoAggregation);
        sourcesPath.attributes(sourceDirectoriesAttributes(project));
        additionalSourceDirs(sourcesPath.getIncoming().artifactView(it -> it.lenient(true)).getFiles());

        Configuration coverageDataPath = createResolver("coverageDataPath");
        coverageDataPath.extendsFrom(jacocoAggregation);
        // TODO: can we add configuration options so that users can choose which test tasks to aggregate coverage for?
        // Given that there will be multiple variants exposing coverage data per test task
        coverageDataPath.attributes(coverageDataAttributes(project));
        executionData(coverageDataPath.getIncoming().artifactView(it -> it.lenient(true)).getFiles().filter(File::exists));
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

    public static Action<? super AttributeContainer> coverageDataAttributes(Project project) {
        return a -> {
            a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            a.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
            a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, "jacoco-coverage-data"));
        };
    }
}
