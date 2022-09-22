package org.myorg;

import java.util.Optional;
import java.net.URI;
import org.gradle.jvm.toolchains.JavaToolchainResolver;
import org.gradle.jvm.toolchains.JavaToolchainRequest;

public abstract class JavaToolchainResolverImplementation
        implements JavaToolchainResolver { // <1>

    public Optional<URI> resolve(JavaToolchainRequest request) { // <2>
        return Optional.empty(); // custom mapping logic goes here instead
    }
}