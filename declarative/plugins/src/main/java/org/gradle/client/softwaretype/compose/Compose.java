package org.gradle.client.softwaretype.compose;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface Compose {
    @Restricted
    Property<String> getAppName();

    @Restricted
    Property<String> getAppDisplayName();

    @Restricted
    Property<String> getAppQualifiedName();

    @Restricted
    RegularFileProperty getAppUUIDFile();

    @Restricted
    Property<String> getMainClass();

    NamedDomainObjectContainer<JvmArg> getJvmArgs();
}
