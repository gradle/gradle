package org.gradle.api.internal.dependencies;

import static org.apache.ivy.core.module.descriptor.Configuration.*;
import org.gradle.api.dependencies.Configuration;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DefaultConfiguration implements Configuration {
    private final String name;
    private DependencyManagerInternal dependencyManager;
    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<String> extendsFrom = new HashSet<String>();
    private String description;
    private String deprecated;

    public DefaultConfiguration(String name, DependencyManagerInternal dependencyManager,
                                org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration) {
        this.name = name;
        this.dependencyManager = dependencyManager;
        if (ivyConfiguration != null) {
            visibility = ivyConfiguration.getVisibility();
            transitive = ivyConfiguration.isTransitive();
            extendsFrom.addAll(Arrays.asList(ivyConfiguration.getExtends()));
            description = ivyConfiguration.getDescription();
            deprecated = ivyConfiguration.getDeprecated();
        }
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visibility == Visibility.PUBLIC;
    }

    public Configuration setVisible(boolean visible) {
        this.visibility = visible ? Visibility.PUBLIC : Visibility.PRIVATE;
        return this;
    }

    public Set<String> getExtendsFrom() {
        return extendsFrom;
    }

    public Configuration setExtendsFrom(Set<String> superConfigs) {
        extendsFrom = superConfigs;
        return this;
    }

    public Configuration extendsFrom(String... superConfigs) {
        extendsFrom.addAll(Arrays.asList(superConfigs));
        return this;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public Configuration setTransitive(boolean t) {
        this.transitive = t;
        return this;
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration() {
        String[] superConfigs = extendsFrom.toArray(new String[extendsFrom.size()]);
        Arrays.sort(superConfigs);
        return new org.apache.ivy.core.module.descriptor.Configuration(name, visibility, description, superConfigs,
                transitive, deprecated);
    }

    public Set<File> resolve() {
        return new HashSet<File>(dependencyManager.resolve(name));
    }

    public String getAsPath() {
        return dependencyManager.antpath(name);
    }
}
