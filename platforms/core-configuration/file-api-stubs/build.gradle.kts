plugins {
    id("gradlebuild.internal.java")
}

description = """Empty compile-time stubs of core-api types referenced by :provider-api's file types.

Never part of the distribution or any published metadata: :provider-api depends on these stubs
with compileOnly, so they leak into no other compile or runtime classpath. Inside the
distribution the real classes from :core-api are used instead.
"""

jvmCompile {
    compilations {
        named("main") {
            // Must not exceed :provider-api's target
            targetJvmVersion = 8
        }
    }
}

errorprone {
    nullawayEnabled = true
}
