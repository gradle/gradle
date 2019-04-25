// tag::apply-cpp-library-plugin[]
plugins {
    `cpp-library`
}
// end::apply-cpp-library-plugin[]

// tag::cpp-library-dependency-mgmt[]
library {
    dependencies {
        api("io.qt:core:5.1")
        implementation("io.qt:network:5.1")
    }
}
// end::cpp-library-dependency-mgmt[]

// tag::cpp-library-configure-target-machines[]
library {
    targetMachines.set(listOf(machines.windows.x86, machines.windows.x86_64, machines.macOS.x86_64, machines.linux.x86_64))
}
// end::cpp-library-configure-target-machines[]

// tag::cpp-library-configure-linkages[]
library {
    linkage.set(listOf(Linkage.STATIC, Linkage.SHARED))
}
// end::cpp-library-configure-linkages[]