package org.gradle.client.softwaretype.desktopcompose;

import org.gradle.api.Action;
import org.gradle.api.experimental.kmp.KmpApplication;
import org.gradle.api.tasks.Nested;
import org.gradle.client.softwaretype.sqldelight.SqlDelight;
import org.gradle.declarative.dsl.model.annotations.Configuring;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface DesktopComposeApplication {
    @Nested
    KmpApplication getKotlinApplication();

    @Configuring
    default void kotlinApplication(Action<? super KmpApplication> action) {
        action.execute(getKotlinApplication());
    }

    @Nested
    SqlDelight getSqlDelight();

    @Configuring
    default void sqlDelight(Action<? super SqlDelight> action) {
        action.execute(getSqlDelight());
    }
}
