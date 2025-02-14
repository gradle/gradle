package org.gradle.client.softwaretype.compose;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.declarative.dsl.model.annotations.Configuring;
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

    @Nested
    BuildTypes getBuildTypes();

    @Configuring
    default void buildTypes(Action<? super BuildTypes> action) {
        action.execute(getBuildTypes());
    }

    @Nested
    NativeDistributions getNativeDistributions();

    @Configuring
    default void nativeDistributions(Action<? super NativeDistributions> action) {
        action.execute(getNativeDistributions());
    }
}
