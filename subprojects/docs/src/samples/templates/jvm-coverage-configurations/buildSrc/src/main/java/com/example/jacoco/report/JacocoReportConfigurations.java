package com.example.jacoco.report;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;

public class JacocoReportConfigurations {

    private Project project;
    private boolean forConsumers;

    public static JacocoReportConfigurations forConsumers(Project project) {
        return new JacocoReportConfigurations(project, true);
    }

    public static JacocoReportConfigurations forProducers(Project project) {
        return new JacocoReportConfigurations(project, false);
    }

    private JacocoReportConfigurations(Project project, boolean forConsumers) {
        this.project = project;
        this.forConsumers = forConsumers;
    }

    public Configuration createCoverageRuntimeConfiguration() {
        final Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.extendsFrom(getCoverageSourceConfiguration());
        return runtimeClasspath;
    }

    public Configuration createRootConfiguration() {
        final Configuration coverageSource = project.getConfigurations().maybeCreate("coverageSource");
        coverageSource.setCanBeConsumed(false);
        coverageSource.setCanBeResolved(false);
        return coverageSource;
    }

    public Configuration createCoverageSourcePathConfiguration() {
        final Configuration coverageSourcePath = createConfiguration("configuration");
        coverageSourcePath.setVisible(false);
        coverageSourcePath.extendsFrom(getCoverageSourceConfiguration());
        final ObjectFactory objects = project.getObjects();
        coverageSourcePath.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
        coverageSourcePath.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
        coverageSourcePath.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, DocsType.SOURCES));
        coverageSourcePath.getAttributes().attribute(Attribute.of("org.gradle.coverageElements", String.class), "sources");
        return coverageSourcePath;
    }

    public Configuration createCoverageDataConfiguration() {
        final Configuration coverageData = createConfiguration("coverageData");
        coverageData.setVisible(false);
        coverageData.extendsFrom(getCoverageSourceConfiguration());
        coverageData.getAttributes().attribute(Attribute.of("org.gradle.coverageElements", String.class), "exec");
        return coverageData;
    }

    private Configuration createConfiguration(String name) {
        final Configuration configuration = project.getConfigurations().create(disambiguateName(name));
        configuration.setCanBeConsumed(!forConsumers);
        configuration.setCanBeResolved(forConsumers);
        return configuration;
    }

    private String disambiguateName(String name) {
        if (forConsumers) {
            name += "Report";
        }
        return name;
    }

    private Configuration getCoverageSourceConfiguration() {
        return project.getConfigurations().getByName("coverageSource");
    }

}
