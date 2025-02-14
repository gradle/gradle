package org.gradle.client.softwaretype.compose;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface Proguard {
    @Restricted
    Property<Boolean> getOptimize();

    @Restricted
    Property<Boolean> getObfuscate();

    ConfigurableFileCollection getConfigurationFiles();
}
