package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolveInstruction;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.artifacts.ConfigurationContainer;
import org.gradle.util.WrapUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DefaultConfiguration implements Configuration {
    private final String name;
    private final ChainingTransformer<org.apache.ivy.core.module.descriptor.Configuration> transformer
            = new ChainingTransformer<org.apache.ivy.core.module.descriptor.Configuration>(org.apache.ivy.core.module.descriptor.Configuration.class);
    private Visibility visibility = Visibility.PUBLIC;
    private Set<Configuration> extendsFrom = new HashSet<Configuration>();
    private String description;
    private ConfigurationContainer configurationContainer;
    private ResolveInstruction resolveInstruction = new ResolveInstruction();

    public DefaultConfiguration(String name, ConfigurationContainer configurationContainer) {
        this.name = name;
        this.configurationContainer = configurationContainer;
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

    public Set<Configuration> getExtendsFrom() {
        return extendsFrom;
    }

    public Configuration setExtendsFrom(Set<String> superConfigs) {
        for (String superConfig : superConfigs) {
            extendsFrom.add(configurationContainer.get(superConfig));
        }
        return this;
    }

    public Configuration extendsFrom(String... superConfigs) {
        setExtendsFrom(new HashSet(Arrays.asList(superConfigs)));
        return this;
    }

    public boolean isTransitive() {
        return resolveInstruction.isTransitive();
    }

    public Configuration setTransitive(boolean t) {
        this.resolveInstruction.setTransitive(t);
        return this;
    }

    public ResolveInstruction getResolveInstruction() {
        return resolveInstruction;
    }

    public String getDescription() {
        return description;
    }

    public Configuration setDescription(String description) {
        this.description = description;
        return this;
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration(boolean transitive) {
        String[] superConfigs = Configurations.getNames(extendsFrom).toArray(new String[extendsFrom.size()]);
        Arrays.sort(superConfigs);
        org.apache.ivy.core.module.descriptor.Configuration configuration = new org.apache.ivy.core.module.descriptor.Configuration(
                name, visibility, description, superConfigs, transitive, null);
        return transformer.transform(configuration);
    }

    public void addIvyTransformer(Transformer<org.apache.ivy.core.module.descriptor.Configuration> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure transformer) {
        this.transformer.add(transformer);
    }

    public Set<Configuration> getChain() {
        Set<Configuration> result = WrapUtil.<Configuration>toSet(this);
        collectSuperConfigs(this, result);
        return result;
    }

    private void collectSuperConfigs(Configuration configuration, Set<Configuration> superConfigs) {
        for (Configuration superConfig : configuration.getExtendsFrom()) {
            superConfigs.add(superConfig);
            collectSuperConfigs(superConfig, superConfigs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConfiguration that = (DefaultConfiguration) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        return result;
    }

    @Override
    public String toString() {
        return "DefaultConfiguration{" +
                "name='" + name + '\'' +
                ", extendsFrom=" + extendsFrom +
                ", description='" + description + '\'' +
                ", visibility=" + visibility +
                '}';
    }
}
