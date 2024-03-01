package org.myorg;

import java.util.Optional;
import java.net.URI;
import org.gradle.jvm.toolchain.JavaToolchainResolver;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainDownload;

// tag::java-toolchain-resolver-implementation[]
public abstract class JavaToolchainResolverImplementation
        implements JavaToolchainResolver { // <1>

    public Optional<JavaToolchainDownload> resolve(JavaToolchainRequest request) { // <2>
        return Optional.empty(); // custom mapping logic goes here instead
    }
}
// end::java-toolchain-resolver-implementation[]
