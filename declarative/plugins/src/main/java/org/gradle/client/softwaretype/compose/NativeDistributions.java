package org.gradle.client.softwaretype.compose;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface NativeDistributions {
    NamedDomainObjectContainer<TargetFormat> getTargetFormats();
}
