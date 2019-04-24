// tag::apply-cpp-plugin[]
plugins {
    `cpp-application` // or `cpp-library`
}

version = '1.2.1'
// end::apply-cpp-plugin[]

// tag::cpp-dependency-mgmt[]
application {
    dependencies {
        implementation(project(":common"))
    }
}
// end::cpp-dependency-mgmt[]

// tag::cpp-compiler-options-all-variants[]
tasks.withType(CppCompile).configureEach {
    // Define a preprocessor macro for every binary
    macros.put("NDEBUG", null)

    // Define a compiler options
    compilerArgs.add("-W3")

    // Define toolchain-specific compiler options
    compilerArgs.addAll(toolChain.map { toolChain ->
        if (toolChain in Gcc) {
            return listOf("-O2", "-fno-access-control")
        } else if (toolChain in VisualCpp) {
            return listOf("/Zi")
        }
        return listOf()
    })
}
// end::cpp-compiler-options-all-variants[]

// tag::cpp-compiler-options-per-variants[]
application {
    binaries.withType(CppStaticLibrary).configureEach {
        // Define a preprocessor macro for every binary
        compileTask.get().macros.put("NDEBUG", null)

        // Define a compiler options
        compileTask.get().compilerArgs.add("-W3")

        // Define toolchain-specific compiler options
        if (toolChain in Gcc) {
            compileTask.get().compilerArgs.addAll(listOf("-O2", "-fno-access-control"))
        } else if (toolChain in VisualCpp) {
            compileTask.get().compilerArgs.add("/Zi")
        }
    }
}
// end::cpp-compiler-options-per-variants[]

// tag::cpp-select-target-machines[]
application {
    targetMachines.set(listOf(machines.windows.x86, machines.windows.x86_64, machines.macOS.x86_64, machines.linux.x86_64))
}
// end::cpp-select-target-machines[]

project(":common") {
    plugins {
        `cpp-library`
    }

    // tag::cpp-source-set[]
    library {
        source.from(file("src"))
        privateHeaders.from(file("src"))
        publicHeaders.from(file("include"))
    }
    // end::cpp-source-set[]
}