package com.example.signing;

import java.io.File;

/**
 * Illustrative stand-in for a third-party library that performs
 * bytecode integrity self-checks. In this snippet the method is a no-op
 * so the snippet compiles and runs; in the docs' narrative it represents
 * a library that would fail under the Gradle Java agent because the agent
 * modifies the bytecode on the build script classpath.
 */
public class SignatureVerifier {
    public static void verify(File artifact) {
        // no-op stub for the doc snippet
    }
}
