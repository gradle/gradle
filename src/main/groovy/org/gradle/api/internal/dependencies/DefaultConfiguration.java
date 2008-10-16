package org.gradle.api.internal.dependencies;

import org.gradle.api.dependencies.Configuration;

public class DefaultConfiguration implements Configuration {
    private final String name;
    private final org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration;

    public DefaultConfiguration(String name, org.apache.ivy.core.module.descriptor.Configuration ivyConfiguration) {
        this.name = name;
        this.ivyConfiguration = ivyConfiguration;
    }

    public String getName() {
        return name;
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration() {
        return ivyConfiguration;
    }
}
