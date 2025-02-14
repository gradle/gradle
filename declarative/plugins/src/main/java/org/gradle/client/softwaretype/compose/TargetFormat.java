package org.gradle.client.softwaretype.compose;

import org.gradle.api.Named;
import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface TargetFormat extends Named {
    Property<org.jetbrains.compose.desktop.application.dsl.TargetFormat> getValue();
}
