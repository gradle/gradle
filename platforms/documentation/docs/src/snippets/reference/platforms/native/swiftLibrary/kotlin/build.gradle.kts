// tag::apply-plugin[]
plugins {
    `swift-library`
}
// end::apply-plugin[]

// tag::dependency-management[]
library {
    dependencies {
        // FIXME: Put real deps here.
        api("io.qt:core:5.1")
        implementation("io.qt:network:5.1")
    }
}
// end::dependency-management[]

// tag::configure-target-machines[]
library {
    targetMachines = listOf(machines.linux.x86_64, machines.macOS.x86_64)
}
// end::configure-target-machines[]

// tag::configure-linkages[]
library {
    linkage = listOf(Linkage.STATIC, Linkage.SHARED)
}
// end::configure-linkages[]
