package org.gradle.client.softwaretype.compose;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public abstract class Compose {
    @Restricted
    public abstract Property<String> getMainClass();

    public abstract NamedDomainObjectContainer<String> getAdditionalJvmArgs();
}
