package org.gradle.api.internal.dependencies;

import static org.apache.ivy.core.module.descriptor.Configuration.*;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ChainingTransformer;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;

import groovy.lang.Closure;

public class DefaultConfiguration implements Configuration {
    private final String name;
    private final ChainingTransformer<org.apache.ivy.core.module.descriptor.Configuration> transformer
            = new ChainingTransformer<org.apache.ivy.core.module.descriptor.Configuration>(org.apache.ivy.core.module.descriptor.Configuration.class);
    private DependencyManagerInternal dependencyManager;
    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<String> extendsFrom = new HashSet<String>();
    private String description;

    public DefaultConfiguration(String name, DependencyManagerInternal dependencyManager) {
        this.name = name;
        this.dependencyManager = dependencyManager;
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

    public String getDescription() {
        return description;
    }

    public Configuration setDescription(String description) {
        this.description = description;
        return this;
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration() {
        String[] superConfigs = extendsFrom.toArray(new String[extendsFrom.size()]);
        Arrays.sort(superConfigs);
        org.apache.ivy.core.module.descriptor.Configuration configuration = new org.apache.ivy.core.module.descriptor.Configuration(
                name, visibility, description, superConfigs, transitive, null);
        return transformer.transform(configuration);
    }

    public Set<File> resolve() {
        return new HashSet<File>(doResolve());
    }

    public String getAsPath() {
        return dependencyManager.antpath(name);
    }

    public File getSingleFile() throws IllegalStateException {
        List<File> files = doResolve();
        if (files.size() != 1) {
            throw new IllegalStateException(String.format("Configuration '%s' does not resolve to a single file.",
                    name));
        }
        return files.get(0);
    }

    public Set<File> getFiles() {
        return resolve();
    }

    public Iterator<File> iterator() {
        return doResolve().iterator();
    }

    private List<File> doResolve() {
        return dependencyManager.resolve(name);
    }

    public void addIvyTransformer(Transformer<org.apache.ivy.core.module.descriptor.Configuration> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure transformer) {
        this.transformer.add(transformer);
    }
}
