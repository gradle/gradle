package org.gradle.client.softwaretype.compose;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;
import org.gradle.declarative.dsl.model.annotations.Configuring;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface BuildType {
    @Nested
    Proguard getProguard();

    @Configuring
    default void proguard(Action<? super Proguard> action) {
        action.execute(getProguard());
    }
}
