package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.jvm.toolchains.JavaToolchainResolverRegistry;

public abstract class JavaToolchainResolverPlugin implements Plugin<Settings> { // <1>
    @Inject
    protected abstract JavaToolchainResolverRegistry getToolchainResolverRegistry(); // <2>

    void apply(Settings settings) {
        settings.getPlugins().apply("jvm-toolchain-management"); // <3>

        JavaToolchainResolverRegistry registry = getToolchainResolverRegistry();
        registry.register(JavaToolchainResolverImplementation.class); // <4>
    }
}