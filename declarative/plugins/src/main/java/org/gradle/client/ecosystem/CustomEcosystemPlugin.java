package org.gradle.client.ecosystem;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes;
import org.gradle.client.softwaretype.CustomDesktopComposeApplicationPlugin;

@SuppressWarnings("UnstableApiUsage")
@RegistersSoftwareTypes({CustomDesktopComposeApplicationPlugin.class})
public abstract class CustomEcosystemPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings target) {}
}
