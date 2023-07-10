package org.myorg;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;

import javax.inject.Inject;

// tag::java-toolchain-resolver-plugin[]
public abstract class JavaToolchainResolverPlugin implements Plugin<Settings> { // <1>
    @Inject
    protected abstract JavaToolchainResolverRegistry getToolchainResolverRegistry(); // <2>

    public void apply(Settings settings) {
        settings.getPlugins().apply("jvm-toolchain-management"); // <3>

        JavaToolchainResolverRegistry registry = getToolchainResolverRegistry();
        registry.register(JavaToolchainResolverImplementation.class);
    }
}
// end::java-toolchain-resolver-plugin[]
