package org.gradle.client.softwaretype.detekt;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface Detekt {
    @Restricted
    ConfigurableFileCollection getSource();

    @Restricted
    ConfigurableFileCollection getConfig();

    @Restricted
    Property<Boolean> getParallel();
}
