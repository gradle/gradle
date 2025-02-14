package org.gradle.client.softwaretype.compose;

import org.gradle.api.provider.Property;
import org.gradle.declarative.dsl.model.annotations.Restricted;
//import org.jetbrains.compose.desktop.application.dsl.TargetFormat;

@Restricted
public interface TargetFormat {
    Property<String> getValue();
}
