package org.gradle.api.internal.tasks.compile;

import org.gradle.api.JavaVersion;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;

public abstract class JvmCompileTask extends AbstractCompile implements HasCompileOptions {
    /**
     * The Java language level to use to compile the source files.
     * Note that the convention for this property is to use the value of {@link #getSourceCompatibilityConvention()} if not set.
     *
     * @return The source language level.
     */
    @Input
    @ReplacesEagerProperty
    @Optional
    @Override
    public abstract Property<String> getSourceCompatibility();

    /**
     * The target JVM to generate the {@code .class} files for.
     * Note that the convention for this property is to use the value of {@link #getTargetCompatibilityConvention()} if not set.
     *
     * @return The target JVM.
     */
    @Input
    @ReplacesEagerProperty
    @Optional
    @Override
    public abstract Property<String> getTargetCompatibility();

    protected abstract Provider<JavaInstallationMetadata> getToolchain();

    @Internal
    @Optional
    public abstract Property<String> getSourceCompatibilityConvention();

    @Internal
    @Optional
    public abstract Property<String> getTargetCompatibilityConvention();

    @Internal
    public Provider<String> getEnvironmentJavaVersion() {
        return getToolchain()
            .map(toolchain -> toolchain.getLanguageVersion().toString())
            .orElse(JavaVersion.current().toString());
    }

    protected void configureCompatibilityOptions(
        DefaultJvmLanguageCompileSpec spec
    ) {
        spec.setSourceCompatibility(
            getSourceCompatibility()
                .orElse(getSourceCompatibilityConvention())
                .orElse(getEnvironmentJavaVersion())
                .getOrNull()
        );
        spec.setTargetCompatibility(
            getTargetCompatibility()
                .orElse(getSourceCompatibility())
                .orElse(getTargetCompatibilityConvention())
                .orElse(getSourceCompatibilityConvention())
                .orElse(getEnvironmentJavaVersion())
                .getOrNull()
        );
    }
}
