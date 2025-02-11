package org.gradle.client.softwaretype;

import org.gradle.api.experimental.kmp.KmpApplication;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface DesktopComposeApplication {
    @Restricted
    KmpApplication getKmpApplication();
}
