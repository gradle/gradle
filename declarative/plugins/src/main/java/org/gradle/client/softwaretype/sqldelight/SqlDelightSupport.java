package org.gradle.client.softwaretype.sqldelight;

import app.cash.sqldelight.gradle.SqlDelightExtension;
import org.gradle.api.Project;
import org.gradle.client.softwaretype.CustomDesktopComposeApplication;

@SuppressWarnings("UnstableApiUsage")
public final class SqlDelightSupport {
    private SqlDelightSupport() { /* not instantiable */ }

    public static boolean needToWireSqlDelight(CustomDesktopComposeApplication dslModel) {
        return !dslModel.getSqlDelight().getDatabases().isEmpty();
    }

    public static void wireSqlDelight(Project project, CustomDesktopComposeApplication dslModel) {
        project.getPluginManager().apply("app.cash.sqldelight");

        dslModel.getKotlinApplication().getTargets().jvm(jvmTarget -> {
            jvmTarget.getDependencies().getImplementation().add("app.cash.sqldelight:runtime:2.0.2");
            jvmTarget.getDependencies().getImplementation().add("app.cash.sqldelight:coroutines-extensions:2.0.2");
            jvmTarget.getDependencies().getImplementation().add("app.cash.sqldelight:sqlite-driver:2.0.2");
        });

        SqlDelightExtension sqlDelight = project.getExtensions().getByType(SqlDelightExtension.class);
        dslModel.getSqlDelight().getDatabases().forEach(dslModelDatabase -> {
            sqlDelight.getDatabases().create(dslModelDatabase.getName(), database -> {
                database.getPackageName().set(dslModelDatabase.getPackageName());
                database.getVerifyDefinitions().set(dslModelDatabase.getVerifyDefinitions());
                database.getVerifyMigrations().set(dslModelDatabase.getVerifyMigrations());
                database.getDeriveSchemaFromMigrations().set(dslModelDatabase.getDeriveSchemaFromMigrations());
                database.getGenerateAsync().set(dslModelDatabase.getGenerateAsync());
            });
        });
    }
}
