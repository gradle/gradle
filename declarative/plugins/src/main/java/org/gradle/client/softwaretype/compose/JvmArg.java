package org.gradle.client.softwaretype.compose;

import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface JvmArg {
    @Restricted
    Property<String> getValue();
}
