package org.gradle.client.softwaretype.compose;

import org.gradle.api.Action;
import org.gradle.api.tasks.Nested;
import org.gradle.declarative.dsl.model.annotations.Configuring;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface BuildTypes {
    @Nested
    BuildType getRelease();

    @Configuring
    default void release(Action<? super BuildType> action) {
        action.execute(getRelease());
    }
}
