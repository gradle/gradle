package org.gradle.client.softwaretype;

import org.gradle.api.Action;
import org.gradle.api.experimental.kmp.KmpApplication;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.client.softwaretype.compose.Compose;
import org.gradle.client.softwaretype.detekt.Detekt;
import org.gradle.client.softwaretype.sqldelight.SqlDelight;
import org.gradle.declarative.dsl.model.annotations.Configuring;
import org.gradle.declarative.dsl.model.annotations.Restricted;

@Restricted
public interface CustomDesktopComposeApplication {
    @Restricted
    Property<String> getGroup();

    @Restricted
    Property<String> getVersion();

    @Nested
    KmpApplication getKotlinApplication();

//    @Configuring
//    default void kotlinApplication(Action<? super KmpApplication> action) {
//        action.execute(getKotlinApplication());
//    }

    @Nested
    Compose getCompose();

    @Configuring
    default void compose(Action<? super Compose> action) {
        action.execute(getCompose());
    }

    @Nested
    SqlDelight getSqlDelight();

    @Configuring
    default void sqlDelight(Action<? super SqlDelight> action) {
        action.execute(getSqlDelight());
    }

    @Nested
    Detekt getDetekt();

    @Configuring
    default void detekt(Action<? super Detekt> action) {
        action.execute(getDetekt());
    }
}
