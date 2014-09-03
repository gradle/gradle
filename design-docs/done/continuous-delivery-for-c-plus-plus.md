## Story: Introduce native binaries (DONE)

This story introduces domain objects to represent each native binary that is built, sharing the concepts introduced in the new JVM language DSL.

This separates the physical binary from the logical component that it is built for. This will be used in later stories to handle building multiple
binaries for a particular native application or library.

- Add `ExecutableBinary` and `SharedLibraryBinary` types, as analogs to `ClassDirectoryBinary` from the JVM domain.
- Extract a `Binary` supertype out of the above types and change the `BinariesContainer` so that it is a container of `Binary` instances.
    - Can remove `JvmBinaryContainer` specialisation.
- Change the `cpp` plugin to:
    - Apply the `lang-base` plugin.
    - Add an `ExecutableBinary` instance to the `binaries` container for each executable added to the `executables` container. The instance should be
      called `${executable.name}Executable`.
    - Add a rule that adds a compile task for each `ExecutableBinary` instance added to the `binaries` container. The task should be called `${binary.name}`.
    - Add a rule that adds an install task for each `ExecutableBinary` instance added to the `binaries` container. The task should be called `install${binary.name}`.
    - Add a `SharedLibraryBinary` instance to the `binaries` container for each library added to the `libraries` container. The instance should be called
      `${library.name}SharedLibrary`.
    - Add a rule that adds a compile task for each `SharedLibraryBinary` instance added to the `binaries` container. The task should be called `${binary.name}`.

*Note*: there's a breaking change here, as the name of the compile task added by the C++ plugins will have changed.

### User visible changes

For this story, a read-only view of the binaries is available:

    apply plugin: 'java'
    apply plugin: 'cpp-exe'
    apply plugin: 'cpp-lib'

    assert binaries.mainClasses instanceof ClassDirectoryBinary
    assert binaries.mainExecutable instanceof ExecutableBinary
    assert binaries.mainSharedLibrary instanceof SharedLibraryBinary

Running `gradle mainExecutable` will build the main executable binary.

### Test cases

- Can apply the `java`, `cpp-exe` and `cpp-lib` plugins together, as shown in the example above.
- For a build that uses the `cpp-exe` plugin:
    - Can run `gradle mainExecutable` to build the main executable.
    - Can run `gradle installMainExecutable` to build the main executable.
- For a build that uses the `cpp-lib` plugin:
    - Can run `gradle mainSharedLibrary` to build the main shared library.

## Story: Separate C++ compilation and linking of binaries (DONE)

This story separates C++ compilation and linking of binaries into separate tasks, so that:

1. Object files built from other languages can be linked into a binary.
2. Object files can be consumed in different ways, such as assembling into a static library or linking into a shared library.

- Split `LinkExecutable` and `LinkSharedLibrary` task types out of `CppCompile` task type.
- Change the `cpp` plugin to add a `CppCompile` and `LinkExecutable` task for each `ExecutableBinary`.
- Change the `cpp` plugin to add a `CppCompile` and `LinkSharedExecutable` task for each `SharedLibraryBinary`.
- Change the `CppCompile` task type to declare its source files, include directories and output directories as properties with
   the appropriate annotations.
- Change the GCC toolchain to:
    - Use `g++` to link the executable and shared libraries.
    - Move the `-Wl` and `-shared` options from compilation to linking.
- Change the visual C++ toolchain to:
    - Use `link.exe` to link the executable and shared libraries.
- Change the link task types to declare their input and output files as properties with the appropriate annotations.
- Introduce a `Toolchain` concept, so that the compiler and linker from the same toolchain are always used together.
- Add these task types to the DSL reference and mark as incubating.

### Test cases

- Incremental build:
    - Changing a compile option causes source files to be compiled and binary to be linked.
    - Changing a link option causes the binary to be linked but does not recompile source files.
    - Changing a comment in a source file causes the source file to be compiled by the but does not relink the binary.

## Story: Build a static library binary (DONE)

This story introduces the concept of a static library binary that can be built for a C++ library. This will allow both shared and static variants of a particular library to be built.

- Add `StaticLibraryBinary` type.
- Add `LinkStaticLibrary` task type.
- Change the `cpp` plugin to:
    - Add a `StaticLibraryBinary` instance to the `binaries` container for each library added to the `libraries` container. The instance should be called
      `${library.name}StaticLibrary`.
    - Add a rule that adds a `CppCompile` and `LinkStaticLibrary` task for each `StaticLibraryBinary` instance added to the `binaries` container. The link task should be
      called `${binary.name}`.
- Change visual C++ toolchain to:
     - Use `lib.exe` to assemble the static library.
- Change the GCC toolchain to:
    - Don't use other shared library flags (`-shared`) when compiling source files for a static library.
    - Use `ar` to assemble the static library.
- Update the user guide to reflect the fact that static libraries can be built. Include a stand-alone sample that
  demonstrates how to build a library.

### User visible changes

Given:

    apply plugin: `cpp-lib`

Running `gradle mainStaticLibrary` will build the main static library. Running `gradle mainSharedLibrary` will build the main shared library.

Given:

    apply plugin: `cpp`

    cpp {
        sourceSets {
            main
        }
    }

    libraries {
        custom {
            sourceSets << cpp.sourceSets.main
        }
    }

Running `gradle customStaticLibrary customSharedLibrary` will build the static and shared binaries.

### Test cases

- For a build that uses the `cpp-lib` plugin, `gradle mainStaticLibrary` will produce the static library.
- For a build that uses the `cpp` plugin and defines multiple libraries, each library can be built as both a static and shared library binary.
- Can link a static library into an executable and install and run the resulting executable.

## Story: Allow customization of binaries before and after linking (DONE)

This story introduces a lifecycle task for each binary, to allow tasks to be wired into the task graph for a given binary. These tasks will be able to modify the object files or binary files before they are consumed.

- Change `Binary` to extend `Buildable`.
- Change `NativeComponent` so that it no longer extends `Buildable`.
- Change the binaries plugin to add a lifecycle task called `${binary.name}`.
- Change the cpp plugin to rename the link tasks for a binary to `link${binary.name}`.
- Change `DefaultClassDirectoryBinary` to implement `getBuildDependencies()` (it currently has an empty implementation).
- Change `CppPlugin` so that the link task for a binary uses the compile task's output as input, rather than depend on the compile task.
- Add an `InstallExecutable` task type and use this to install an executable.
- Change `CppPlugin` so that the install task for an executable uses the executable binary as input, rather than depend on the link task.
- Add `NativeBinary.tasks` property of type `NativeBinaryTasks` that is a `DomainObjectSet<Task>` containing key tasks for a binary.
     - `NativeBinaryTasks.link` and `NativeBinaryTasks.createStaticLib` are convenience methods for accessing the link/create tasks for a binary.

*Note*: There is a breaking change here, as the link task for a given binary has been renamed.

### User visible changes

    apply plugin: 'cpp'

    binaries.all { binary ->
        def stripTask = task("${binary.name}Strip") {
            dependsOn binary.tasks.link
            doFirst {
                ["strip", binary.tasks.link.outputFile].execute()
            }
        }
        binary.builtBy stripTask
    }

## Story: Allow customization of binary compilation and linking (DONE)

This story allows some configuration of the settings used to compile and link a given binary.

Some initial support for settings that will be shared by all binaries of a component will also be added.

Later stories will add more flexible and convenient support for customization of the settings for a binary.

- Add the following mutable properties to `NativeBinary`:
    - `outputFile`
    - `objectFileDirectory`
    - `compilerArgs`
- Add the following mutable properties to `ExecutableBinary` and `SharedLibraryBinary`:
    - `linkerArgs`
- Add a `binaries` property to `NativeComponent`. This is a `DomainObjectCollection` of all binaries for the component.
- Remove `NativeComponent.compilerArgs` and `linkerArgs` properties. Instead, configuration injection via the `binaries` container can be used to
  define shared settings for the binaries of a component.

### User visible changes

    apply plugin: 'cpp-lib'

    libraries {
        main {
            // Defaults for all binaries for this library
            binaries.all {
                compilerArgs = ['-DSOME_DEFINE']
                linkerArgs = ['-lsomelib']
            }
            // Defaults for all shared library binaries for this library
            binaries.withType(SharedLibraryBinary) {
                compilerArgs << '/DDLL_EXPORT'
            }
        }
    }

    binaries {
        mainSharedLibrary {
            // Adjust the args for this binary
            compileArgs << '-DSOME_OTHER_DEFINE'
            linkerArgs << '-lotherlib
        }
    }

    binaries.withType(NativeBinary) {
        outputFile = file("$buildDir/${name}/${outputFileName}")
        objectFileDirectory = file("$buildDir/${name}/obj")
    }

## Story: Ensure CI builds exercise test coverage for supported tool chains (DONE)

The CI builds include coverage for each supported tool chain. However, the coverage silently ignores tool chains which are not
available on the current machine. Instead, the CI builds should asert that every expected tool chain is avilable on the current
machine.

Later stories will add further integration test coverage for particular OS and tool chain combinations.

- Change `AbstractBinariesIntegrationSpec` to assert that each expected tool chain is installed on the current machine when
  runnning as part of a CI coverage build.
- Change `AbstractBinariesIntegrationSpec` to use a single tool chain for each machine as part of a CI commit build. For Windows,
  the test should use a recent version of Visual C++, and for Linux, the test should use GCC.
- Install Visual C++ 2010 express, Cygwin and MinGW on the Windows CI agents, as required.
- Install GCC 3 and GCC 4 the linux CI agents, as required.
- Update the server wiki pages to describe the installation steps required for each machine.

## Story: Compile C source files using the C compiler

This story adds support for C source files as inputs to native binaries.

- Add a `CSourceSet` interface.
- Add a `CCompile` task type.
- Change the `cpp` plugin to add a `CCompile` instance for each `CSourceSet` added to a native binary.
- Change the visual C++ toolchain to:
    - Not use `/EHsc` when compiling any source.
    - Use `/TC` to force source language to C for all files in a C source set.
    - Use `/TP` to force source language to C++ for all files in a C++ source set.
- Change the GCC toolchain to:
    - Use `gcc` to compile all source files in a C source set. Should also use `-x c` to force source language to C.
    - Use `g++` to compile all source files in a C++ source set. Should also use `-x c++` to force source language to C++.
- Change `compilerArgs`, `define` and `macros` methods on NativeBinary so that they are language-specific
    - Replace existing methods with `cppCompiler.args`, `cppCompiler.define` and `cppCompiler.macros`.
    - Introduce `cCompiler` equivalents

### Test cases

- Build breaks when C++ source file cannot be compiled.
- Build breaks when C source file cannot be compiled.
- Build breaks when a binary cannot be linked, for each type of binary.
- C compilation is incremental wrt C source files, header files and compiler settings.
- Mixed C/C++ binary, for each type of binary.
- Project has mixed C and C++ header files.
- Verify that C compiler is being used for C, and C++ compiler is being used for C++.
- Manually define C++ and C source sets.
- Can build a binary from C sources with `gcc` when `g++` and `as` are not installed

## Story: Build a binary from assembler source files

This story adds support for using assembler source files as inputs to a native binary.

- Add an `AssemblerSourceSet` type and allow these to be added to a native binary.
- Add an `Assemble` task type.
- Change the `cpp` plugin to add an `Assemble` instance for each assembler source set added to a native binary.
- Change the visual C++ toolchain to:
    - Use `ml /nologo /c` to assemble a source file to an object file.
- Change the GCC toolchain to:
    - Use `as` to assemble a source file to an object file.
- Add `NativeBinary.assembler.args` for providing arguments to the assembler

### Test cases

- Build breaks when source file cannot be assembled.
- Assembly is incremental wrt assembly source files, and assembler settings.
- Mixed C/C++/ASM binary, for each kind of binary
- A project can have all C, C++ source and header files and assembly source files in the same source directory.
- Manually define an assembler source set.

## Story: Introduce native functional source sets

This story reworks the existing C++ source set concepts to reuse the concept of a *functional* source set from the new JVM language DSL. This will allow a binary to be built from more than one language, and later stories will build on this to add support for specific languages.

- Change `CppSourceSet` to extend `LanguageSourceSet`.
- Add a mutable `NativeBinary.source` property of type `DomainObjectCollection<LanguageSourceSet>`.
    - Add a `NativeBinary.source` method that accepts a `FunctionalSourceSet` as input,
      and adds every `LanguageSourceSet` included in that functional source set.
- Change the C++ plugin to add a component's source sets to each of the component's binaries.
- Change the C++ plugin to add a `CppCompile` instance for each `CppSourceSet` added to a native binary.
- Change the `cpp` plugin to:
    - Add a `sources.${functionalSourceSet.name}.cpp` C++ source set for every functional source set.
- Change the `cpp-exe` and `cpp-lib` plugins to:
    - Add a `sources.main` functional source set
    - Wire the `sources.main.cpp` source set into the `main` executable/library.
- Remove `CppExtension`

### User visible changes

To adjust the by-convention layout:

    apply plugin: 'cpp-lib'

    sources {
        main {
            cpp {
                source.srcDirs = 'src'
            }
        }
    }

To define custom source sets and components:

    apply plugin: `cpp`

    sources {
        util {
        }
        app {
            cpp.libs << libraries.util
        }
        windows {
        }
    }

    libraries {
        util {
            source sources.util
        }
        app {
            source sources.app.cpp
        }
    }

### Test cases

- Assemble a binary for a component with no source.
- Assemble a binary for a component with multiple source sets.
- Assemble a binary from a functional source set.
- Assemble a binary from a cpp source set.
- Attempt to build a binary from a Java source set or a resource set.

## Story: Allow library binaries to be used as input to executable binaries

This story adds support for using another library component or binary as input to compile and/or link a given binary. Adding a library component or binary as input
implies that the library's header files will be available at compilation time, and the appropriate binaries available at link and runtime.

Some support will be added for expressing shared dependencies at the component level as well at the binary level.

Later stories will build on this to unify the library dependency DSL, so that a consistent approach is used to consume libraries built by the same project, or another
project in the same build, or from a binary repository.

- Rename `NativeDependencySet.files` to `runtimeFiles`.
- Add `NativeDependencySet.linkFiles`. Default implementation should return the runtime files. This will be improved in later stories.
- Change `DefaultStaticLibrary` so that the output file is called `lib${library.baseName}.a` when building with GCC on Windows.
- Add `LibraryBinary.getAsNativeDependencySet()`. The implementation depends on toolchain and binary type:

<table>
    <tr>
        <th>Binary type</th>
        <th>Toolchain</th>
        <th>linkFiles</th>
        <th>runtimeFiles</th>
    </tr>
    <tr><td>Shared</td><td>Visual C++</td><td>the `.lib` file</td><td>the `.dll` file</td></tr>
    <tr><td>Static</td><td>Visual C++</td><td>the `.lib` file</td><td>empty collection</td></tr>
    <tr><td>Shared</td><td>GCC</td><td>the `.so` or `.dll` file</td><td>the `.so` or `.dll` file</td></tr>
    <tr><td>Static</td><td>GCC</td><td>the `.a` file</td><td>empty collection</td></tr>
</table>

- The `includeRoots` property of the `NativeDependencySet` implementation for a binary should return the set of header directories exported by the library.
- Add `NativeBinary.libraries` property of type `DomainObjectCollection<NativeDependencySet>`.
- Change the C++ plugin to add the dependencies of each C++ source set to this collection.
- Change the C++ plugin to wire the inputs of the binary's compile and link tasks based on the contents of the `NativeBinary.libraries` collection.
- Add `Library.getShared()` and `getStatic()` methods which return `NativeDependencySet` implementations for the shared and static variants of the library.
- Add `NativeBinary.lib(Object)` method which accepts anything that can be converted to a `NativeDependencySet`:
    - A `NativeBinary` is converted by calling its `getAsNativeDependencySet()` method.
    - A `NativeDependencySet` can be used as is.
    - A `Library` is converted by calling its `getShared().getAsNativeDependencySet()` method.
- Add `CppSourceSet.lib(Object)` method which accepts the above types.

Note that this story does not include support for including the transitive dependencies of a library at link time and runtime.

### User visible changes

    binaries {
        mainExecutable {
            // Include the static variant of the given library in the executable
            lib libraries.main.static
            // Include the default variant of the given library in the executable
            lib libraries.utils
            // Include the given binary in the executable
            lib binaries.someSharedLibrary
        }
    }

    cpp {
        sourceSets {
            main {
                lib libraries.main
                lib libraries.util.shared
                lib binaries.someSharedLibrary
            }
        }
    }

### Test cases

- Install and run an executable that:
    - Uses a mix of static and shared libraries.
    - Uses a mix of libraries from the same project, same build and from different builds.
    - In each case, verify that only shared libraries are included in the install image.
    - In each case, remove the original binaries before running the install image.
- A dependency on a binary overrides a dependency on the library that produced the binary.

## Story: Simplify configuration of component source sets

In the majority of cases, it makes sense to have a single named functional source set for each component, with
the source set name and source directory names matching the component name. This story ensures that such a source set
are created automatically for each component.

- Add a rule to the 'native-binaries' plugin that for each native component:
    - Creates a functional source set named with the component name, if such a source set does not yet exist.
    - Configures the functional source set to be a source input for the component.
- Remove explicit configuration and wiring of functional source sets for each component from tests and samples.

### Test cases

- Programmatically create the functional source set prior to adding the matching component.

## Story: Build different variants of a native component

This story adds initial support for building multiple variants of a native component. For each variant of a component, a binary of the appropriate type is defined.

- Add a `flavors` container to `NativeComponent`
- If no flavors are defined, then implicitly define a `default` flavor.
- Variants will be built for all available flavors:
    - With a single flavor, the binary task names and output directories will NOT contain the flavor name.
    - With multiple flavors, task names and output directories for each variant will include the flavor name.
- Add a property to `NativeBinary` to allow navigation to the flavor for this binary.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`:
    - If `l` has multiple flavors defined, select the binary with the same flavor as `b`. Fail if no binary with matching flavor. Match flavors based on their name.
    - If `l` has a single flavor (default or defined), select the binary with that flavor.

### User visible changes

    libraries {
        main {
            flavors {
                main {}
                withOptionalFeature {}
            }
            binaries.matching { flavor == flavors.main } {
                cppCompiler.args '-DSOME_FEATURE
            }
        }
    }

This will define 4 binaries:

- library: 'main', flavor: 'main', packaging: 'static'
- library: 'main', flavor: 'main', packaging: 'shared'
- library: 'main', flavor: 'withOptionalFeature', packaging: 'static'
- library: 'main', flavor: 'withOptionalFeature', packaging: 'shared'

### Test cases

- Executable with flavors depends on a library with matching flavors.
    - Verify that each flavor of the executable can be built and is linked against the correct flavor of the library
- Executable with flavors depends on a library with no flavors
- Executable with flavors depends on a library with a single non-default flavor
- Executable depends on a library depends on another library. All have the same set of flavors
    - Verify that each flavor of the executable can be built and is linked against the correct flavor of the libraries.
- Executable with flavors depends on a library with a single flavor which depends on a library with flavors.
- Reasonable error message when:
    - Executable depends on a library with multiple flavors
    - Executable with flavors depends on a library with multiple flavors that do not match the executable's flavors.

## Story: Build a native component using multiple tool chains

This story adds support for building a native component using multiple tool chains. Each variant will have a tool chain associated with it.

- Build author can define a set of tool chain that may be used to build a component.
- If no tool chain is defined, then a single default tool chain will be used.
- From the set of defined tool chains, a set of available tool chains will be determined for building.
- Variants will be built for all available tool chains.
    - With a single defined tool chain, the binary task names and output directories will NOT contain the tool chain name.
    - With multiple defined tool chains, task names and output directories for each variant will include the tool chain name.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`:
    - If `l` has multiple toolchains defined, select the binary with the same toolchain as `b`. Fail if no binary with matching toolchain. Match toolchains based on their name.
    - If `l` has a single toolchain, select the binary with that toolchain.
    - In both instances, assert that the toolchain for the selected binary is ABI compatible with the toolchain being used.
- When building component, will attempt to build variants for all available tool chains.

### User visible changes

    // Global set of defined tool chains. A future story may allow this to be tweaked per-component
    toolChains {
        gcc(Gcc) {} // binaries with default names, found on Path
        gcc3(Gcc) {
            path "/opt/gcc/3.4.6/bin" // binaries with default names, found in supplied path
        }
        mingw(Gcc) {
            path "C:/MinGW/bin"
        }
        gccCrossCompiler(Gcc) {
            path "/opt/gcc/4.0/bin"

            // Custom binary file paths and arguments
            cCompiler.executable "gccCustom_gcc"
            cppCompiler.executable project.file("/usr/bin/gccCustom_g++")
            assembler.executable "/usr/bin/gccCustom_as"
            linker.executable "gcc"
            staticLibArchiver.executable "ar"
        }

        // Locate Visual Studio installation by path or in default locations
        visualCpp(VisualCpp) {}

        // Explicit configuration of Visual Studio installation directory
        visualCpp(VisualCpp) {
            installDir "D:/Programs/Microsoft Visual Studio 10.0"
        }
    }

### Tests

- With no toolchain defined, build on windows with Visual C++/MinGW/cygwin-gcc in path and on linux with gcc4/clang in path. (existing tests)
- Build with clang & gcc4 on Linux in a single invocation, with neither tool chain in path.
- Build with Visual C++, MinGW and GCC on Windows in a single invocation, with no tool chain in path.
- Reasonable error message when no tool chain is defined and default is not available.
- Reasonable error message any defined tool chain not available.
- Build an executable with multiple toolchains that uses a library with a single toolchain that uses a library with multiple toolchains.

## Story: Build a native component for multiple architectures

This story adds support for building a component for multiple architectures.
Introduce the concept of a native platform, which may have an associated architecture and operating system.
Each ToolChain can be asked if it can build for a particular platform.
Each variant has a platform associated with it.

- Native binaries plugin adds new types: `Architecture`, `Platform` and `PlatformContainer`.
    - Add an extension to `project` of type `PlatformContainer` named `targetPlatforms`.
- Add `architecture` property to `Platform`
    - If a platform has no architecture defined, then the architecture of the build machine is used.
    - If no target platform is defined and added to the PlatformContainer, then the a single default platform is added.
- Add a `platform` property to `NativeBinary` to allow navigation from the binary to its corresponding platform.
- Split `PlatformToolChain` out of `ToolChainInternal`, with `ToolChainInternal.target(Platform) -> PlatformToolChain`
    - `PlatformToolChain` may contain built-in knowledge of arguments required to build for target platform.
    - Additionally, platform-specific arguments may be supplied in the build script
- For each tool chain, a variant will be built for each defined platform. Later stories will allow a tool chain to only target certain platforms.
    - With a single defined platform, the binary task names and output directories will NOT contain the platform name.
    - With multiple defined platforms, task names and output directories for each variant will include the platform name.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`:
    - If `l` has multiple platforms defined, select the binary with the same platform as `b`. Fail if no binary with matching platform. Match platforms based on their name.
    - If `l` has a single platform, select the binary with that platform.
    - In both instances, assert that the platform for the selected binary is compatible with the platform the binary is being built for
- Change the GCC and Visual C++ toolchain adapters to invoke the assembler, compiler, linker and static lib bundler with the appropriate architecture flags.
- Update the `c-with-assembler` sample to remove the command-line args and to use the DSL to select the appropriate source sets.

### User visible changes

    // Project definition of target platforms.
    // Later stories may allow this to be separately configured per component.
    targetPlatforms {
        x86_standard {
            architecture "x86" // Synonym for x86-32, IA-32
        }
        x86_optimised {
            architecture "x86" // Synonym for x86-32, IA-32
        }
        x64 {
            architecture "x64" // Synonym for x86-64, AMD64
        }
        itanium {
            architecture "IA-64"
        }
    }

    binaries.all {
        if (binary.platform == platforms.x86_optimised) {
            // Customize arguments for "x86_optimised" variants
        }

        if (binary.platform.architecture.name == "x86") {
            // Customize arguments for all "x86_standard" and "x86_optimised" variants
        }
    }

### Tests

- For each supported toolchain, build a 32-bit binary on a 64-bit machine.
- Verify that the correct output was produced
    - On linux run `readelf` over the object files and binaries
    - On OS X run `otool -hv` over the object files and binaries
    - On Windows run `dumpbin` over the object files and binaries
- Build an executable with multiple architectures that uses a library with a single architecture that uses a library with multiple architectures.

## Story: Produce build type variants of a native component

This story adds support for building variants of a native component based on 'build type'.
The set of build types is user configurable, and for the purpose of this story, all build type customisations are specified in the build script.
Standard build types (debug/release) and build type compatibility will be handled in a subsequent story.

- NativeBinaries plugin adds a `buildTypes` container to the project.
- `BuildType` is a simple type extending `Named` with no additional properties.
- If no build types are configured, add a single default build type named `debug`
- Build a variant Native Binary for each defined build type. Add a property to NativeBinary to allow navigation to the defined `buildType`.
    - With a single defined (or default) build type, the binary task names and output directories will NOT contain the build type.
    - With multiple defined build types, task names and output directories for each variant will include the build type.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`:
    - Select a binary for library `l` that has a build type with a matching name.

## Story: Use GCC to cross-compile for custom target platforms

This story adds support for cross-compilation with GCC. It adds the concept of a 'platform configuration' to be supplied to a tool chain,
specifying arguments to use with GCC when targeting a particular platform.

- Add `TargetPlatformConfiguration` that defines the set of tool arguments that should be provided to the tool chain
  when building for a target platform.
- Add `Gcc.addPlatformConfiguration(TargetPlatformConfiguration)` to add target platform support to a tool chain.
- Add `ToolChainInternal.canTargetPlatform(Platform)` to determine if a particular tool chain supports building for a particular
  target platform.
  - For GCC 'i386' and 'x86_64' will have built-in support.
  - For Visual C++, the supported platforms is not extensible. Support is limited to 'i386', 'x86_64' and 'ia64'.

### User visible changes
    toolChains {
        crossCompiler(Gcc) {
            addPlatformConfiguration(new ArmSupport())
        }
    }
    targetPlatforms {
        arm {
            architecture "arm"

        }
        x64 {
            architecture "x86_64"
        }
    }

    class ArmSupport implements TargetPlatformConfiguration {
        boolean supportsPlatform(Platform element) {
            return element.getArchitecture().name == "arm"
        }

        List<String> getCppCompilerArgs() {
            ["-mcpu=arm"]
        }

        List<String> getCCompilerArgs() {
            ["-mcpu=arm"]
        }

        List<String> getAssemblerArgs() {
            []
        }

        List<String> getLinkerArgs() {
            ["-arch", "arm"]
        }

        List<String> getStaticLibraryArchiverArgs() {
            []
        }
    }

## Story: Build a native component for multiple operating systems

This story adds the concept of an operating system to the platform. A tool chain may only be able to build for a particular
target operating system, an for GCC additional support may be added to target an operating system.

- Add `OperatingSystem` and simple type extending Named
- Add `Platform.operatingSystem` property. Default is the default target operating system of the tool chain.
    - Can configure the operating system with a string arg to `Platform.operatingSystem()`: a set of standard names will map to
      windows, linux, osx and solaris.

### User visible changes

    targetPlatforms {
        linux_x86 {
            operatingSystem "linux"
            architecture "x86"
        }
        linux_x64 {
            operatingSystem "linux"
            architecture "x64"
        }
        osx_64 {
            operatingSystem "osx"
            architecture "x64"
        }
        itanium {
            architecture "IA-64"
            // Use the tool chain default operating system
        }
    }

## Story: Incremental compilation for C and C++

### Implementation

There are two approaches to extracting the dependency information from source files: parse the source files looking for `#include` directives, or
use the toolchain's preprocessor.

For Visual studio, can run with `/P` and parse the resulting `.i` file to extract `#line nnn "filename"` directives. In practise, this is pretty slow.
For example, a simple source file that includes `Windows.h` generates 2.7Mb in text output.

For GCC, can run with `-M` or `-MM` and parse the resulting make file to extract the header dependencies.

The implementation will also remove stale object files.

### Test cases

- Change a header file that is included by one source file, only that source file is recompiled.
- Change a header file that is not included by any source files, no compilation or linking should happen.
- Change a header file that is included by another header file, only the source files that include this header are recompiled.
- Cyclic dependencies between headers.
- Change a compiler setting, all source files are recompiled.
- Remove a source file, the corresponding object file is removed.
- Rename a source file, the corresponding object file is removed.
- Remove all source files, all object files and binary files are removed.
- Rename a header file, only source files that include this header file directly or indirectly are recompiled.
- Remove a header file, source files that include this header directly or indirectly are recompiled and compilation fails.
- Move a header file from one source directory to another.
- Error handling:
    - Change a source file so that it contains an error. Compilation fails.
    - Change the source file to fix the error and change another source file. Compilation succeeds.

### Open issues

- Implementation currently locks the task artifact cache while compiling

## Story: Modify command line arguments for binary tool prior to execution

This story provides a 'hook' allowing the build author to control the exact set of arguments passed to a tool chain executable.
This will allow a build author to work around any limitations in Gradle, or incorrect assumptions that Gradle makes.
The arguments hook should be seen as a 'last-resort' mechanism, with preference given to truly modelling the underlying domain.

### User visible changes

    toolChains {
        gcc(Gcc) {
            cppCompiler.withArgs { List<String> args ->
                Collections.replaceAll(args, "-m32", "-march=i386")
            }
            linker.withArgs { List<String> args ->
                if (args.remove("-lstdc++")) {
                    args << "-lstdc++"
                }
            }
        }
    }

### Implementation

- Change the mechanism for transforming a spec to arguments, so that first we create a list of arguments, then we apply
  a series of transformations.
- Add `GccTool.withArgs(Action<List<String>>)`, which adds a transformation early in the series (before writing option file).

### Test cases

- Test with custom placeholder argument that is replaced by a GCC argument via GccTool.withArgs(). Do this for each tool (cppCompiler, cCompiler, linker, assembler, archiver).
- Test adds bad argument to args for binary: arg is removed by hook.

## Story: Support Clang compiler

### Test cases

- Build all types of binaries from all supported languages using Clang.
- Change test apps to detect which compiler is used to compile and assert that the correct compiler is being used.

## Files with identical names in C/C++ source tree are silently excluded from compilation (GRADLE-2923)

### Implementation

* Change language compiler implementations (GCC and VisualCpp) so that only a single sources file is compiled per execution:
    * Create a single compiler options file to reuse for compiling all source files
    * For each source file specify the source file name and the output file name on the command line
* Ensure that the generated object files differentiate source files with the same name and different path
    * Given a source file, calculate the hash of the path of the directory containing the source file
    * Generate the object file into: <task-output-dir>/<hash-of-directory-that-contains-the-source-file>/<source-file-name>.<object-file-extension>
* Update `OutputCleaningCompiler` with knowledge of the new source->object file mapping

### Test cases

* C source set includes files with the same name in different directories. (✓)
* C++ source set includes files with the same name in different directories. (✓)
* Objective-C source set includes files with the same name in different directories.(✓)
* Objective-C++ source set includes files with the same name in different directories. (✓)
* Assemble source set includes files with the same name in different directories. (✓)
* Windows resource source set includes files with the same name in different directories. (✓)
* C, C++, Objective-C and Objective-C++ source sets include files with the same base name (eg there's a `foo.c`, `foo.cpp`, `foo.m` and `foo.mm` in the same directory) (✓, apart from objc/cpp)
* Removes stale outputs when source file is moved to a different directory (✓)

## Story: Link Windows resource files into native binaries

Allow resource files to be linked into a Windows binary.

This story adds a new `windows-resources` plugin which adds support for compiling and linking resource files into a native binary. Windows resource will be treated
as another native language, and the plugin will follow a similar pattern to the C and C++ language plugins.

Here's an example:

    apply plugin: `windows-resources`

    sources {
        myExe {
            rc {
                include '**/*.rc'
            }
        }
    }

    executables {
        myExe
    }

1. Add a `windows-resources` plugin. This will follow a similar pattern as the `cpp` and `c` plugins (see `CppPlugin` for example). For now, this can do nothing.
2. Add a `WindowsResourcesSet` which extends `LanguageSourceSet`.
3. For each `FunctionalSourceSet`, the `windows-resources` plugin defines a `WindowsResourceSet` called `rc` (see `CppLangPlugin` for example).
4. Add a `WindowsResourcesCompile` task type. For now, this can do nothing.
5. For each `WindowsResourceSet` added as input to a `NativeBinary`, then the `windows-resources` plugin:
    - If target platform is not Windows, ignores the source set.
    - If target platform is Windows, adds a `WindowsResourceCompile` task which takes as input all of the resource files in the source set.
      Naming scheme as per the other language plugins (see `CppNativeBinariesPlugin` for example).
6. The `WindowsResourcesCompile` task compiles source resources to `.res` format:
    - For the Visual C++ toolchain, should use [`rc`](http://msdn.microsoft.com/en-us/library/windows/desktop/aa381055.aspx) to compile each source file.
    - Should implement this by adding a new tool method on `PlatformToolChain`.
7. Include the resulting `.res` files as input to the link task  (see `CppNativeBinariesPlugin` for example).
8. For each `NativeBinary`, the `windows-resources` plugin defines a `PreprocessingTool` extension called `rcCompiler` (see `CppNativeBinariesPlugin` for example).
    - If the target platform is not Windows, do not add.
    - Wire up the args and macros defined in this extension as defaults for the resource compile task.
9. Add a sample.

### Test cases

- Can compile and link multiple resource files into an executable and a shared library.
    - Possibly use a STRING resource and the `LoadString()` function to verify that the resources are included.
    - Possibly find some tool that can query the resources linked into a binary and use this.
- Can define preprocessor macros and provide command-line args to `rc` and `windres`.
- Compilation and linking is incremental wrt resource source files and resource compiler args.
- Stale `.res` files are removed when a resource source file is renamed.
- User receives a reasonable error message when resource compilation fails.
- Can create a resources-only executable and shared library.
- Resource source files are ignored on non-Windows platforms.

## Story: Allow a component to choose from a set of defined Platform, BuildType and Flavor instances

- Move `executables` and `libraries` collections into model DSL.

### User visible changes

    model {
        platforms {
            win32 {
                architecture "i386"
                operatingSystem "windows"
            }
            linux32 {
                architecture "i386"
                operatingSystem "linux"
            }
            linux64 {
                architecture "amd64"
                operatingSystem "linux"
            }
        }
        buildTypes {
            debug
            release
        }
        flavors {
            free
            paid
        }
    }
    executables {
        main {
            targetPlatforms "linux32", "linux64"
            targetFlavors "paid"
            targetBuildTypes "debug", "release" // same as default
        }
    }

### Test cases

- Target a particular platform, or target all platforms if not specified
- Target a particular flavor, or target all flavors if not specified
- Target a particular build type, or target all build types if not specified
- Fails with reasonable exception when supplied name does not match any available element.

### Open issues

- Provide instance instead of name to selector DSL: `platforms.win32`. This will require that executables are created in a model rule.
- Selector DSL to choose all platforms with a particular operating system or architecture.
- Accept a collection of values to make it easy to use flavors.matching({}) or buildTypes.matching({})
- Possibly use a single `target` method to accept a platform, buildType or flavor selector. Would require that selectors are typed.
- Add conventional platforms, build types and flavor
- When none targeted, choose a single default platform, build type and/or flavor to target.

### Story: Compile Objective-C and ObjectiveC++ source files

- Apply pull request: https://github.com/gradle/gradle/pull/222
- Add integration test coverage, as below
- Update documentation:
    - Mention in the 'native binaries' user guide chapter.
    - Add types and extensions to DSL reference.
    - List the new plugins in the 'standard plugins'
    - Add Objective-C and Objective-C++ samples.

#### Test cases

- Add `HelloWorldApp` implementation based on Objective-C and add `AbstractLanguageIntegrationTest` and `AbstractLanguageIncrementalBuildIntegrationTest` subclasses
  that use this.
- Add `HelloWorldApp` implementation based on Objective-C++ and add `AbstractLanguageIntegrationTest` and `AbstractLanguageIncrementalBuildIntegrationTest` subclasses
  that use this.
- Add `HelloWorldApp` implementation that uses a mix of C, C++, Objective-C and Objective-C++ as for `MixedLanguageIntegrationTest`.
- Source layout for Objective-C and Objective-C++ can be customised.
- Reasonable error message when attempting to build binary from Objective-C or Objective-C++ when using Visual Studio.

### Story: Incremental compilation for Objective-C and Objective-C++

- Change the Objective-C and Objective-C++ task implementations to apply incremental compilation, similar to the C and C++ tasks.
- Source import parsing should understand `#import` directive.

#### Test cases

- Add an `AbstractLanguageIncrementalCompileIntegrationTest` subclass for each of Objective-C and Objective-C++
- Source file uses `#include` to include a header file.
- Source file uses `#import` to include a header file.

## Story: Improved DSL for platform-specific tool chain configuration

The Gradle model for native binaries will describe the concepts of Build Type and Platform in abstract terms, not specific
to a particular tool chain. It is the job of the tool chain to map these concepts to concrete command-line arguments where possible.

This story improves the mechanism whereby a tool chain supplies arguments specific to a Platform for compiling/linking etc, and makes
this consistent with the way that tool arguments are configured in a tool chain. A future story will improve this mechanism.

### Implementation

* Split `ConfigurableToolChain` (with the GccTool methods) out of `PlatformConfigurableToolChain`
* Replace `PlatformConfigurableToolChain.addPlatformConfiguration` with `PlatformConfigurableToolChain.target(Platform..., Action<ConfigurableToolChain>)`
* Replace the built-in `TargetPlatformConfiguration` actions with `Action<ConfigurableToolChain>`
* If `PlatformConfigurableToolChain.target()` is called on a tool chain, then the default target configurations are removed.
* Documentation describes how to define a new platform.
* Documentation describes how to configure a tool for an existing platform.

### User visible changes

    model {
        toolChains {
            gcc(Gcc) {
                // Default platform support is _replaced_ by configured support

                // Build for 'platforms.sparc' with no additional arguments
                target(platforms.sparc)

                // Build for 'platforms-ultrasparc' with different executable
                target(platforms.ultrasparc) {
                    cppCompiler.executable "foo-bar"
                }

                // Build for any windows platform with these args
                target(platforms.matching { operatingSystem == operatingSystems.windows } ) {
                    cppCompiler.withArguments { args ->
                        args.replace("foo", "bar")
                        args.add("-m32")
                    }
                }
            }
        }
    }

### Test cases

* Configure a GCC tool chain that creates x86 binaries when targeting amd64
* Configure a GCC tool chain that cannot create x86 binaries
* Configure a GCC tool chain that always uses a custom compiler arg when targeting x86

## Story: Modify command line arguments for visualCpp toolchain prior execution

provide a 'hook' allowing the build author to control the exact set of arguments passed to a visualcpp toolchain executable.
This will allow a build author to work around any limitations in Gradle, or incorrect assumptions that Gradle makes.

### Implementation

* Change `VisualCppToolChain` to extend `ExtendableToolChain` and register `linker` and `staticLibArchiver` tools
* Move registration of cpp, windows-rc and assembler tools in VisualCppToolChain to according plugins
* Extract `CommandLineToolChain` interface out of `Gcc` and introduce similar functionality to VisualCpp and Clang tool chains.
* Move setter/getter of executables in into GccCommandLineToolConfiguration
* Add according documentation to userguide/DSL reference and update release notes

### User visible changes

		apply plugin:'cpp'

		model {
            toolChains {
                visualCpp(VisualCpp) {
                    cppCompiler.withArguments { args ->
                            args << "-DFRENCH"
                    }
                }
            }
        }


### Test coverage

* Can tweak arguments for VisualCpp, Gcc and Clang

## Story: Allow configuration of tool chain executables on a per-platform basis (Gcc based toolchains)

### Implementation

* In AbstractGccCompatibleToolChain change initTools method to configure configurableToolChain instead after targetPlatformConfigurationConfiguration is applied

### Test coverage

* Can use g++ instead of gcc for compiling C sources
* Can use custom executable

### Open issues

* path configuration is currently not possible on a per platform basis. full paths to executables must be used or executable must be on
system path.

## Story: Improved DSL for tool chain configuration

This story improves the DSL for tweaking arguments for a command-line tool that is part of a tool chain, and extends this
ability to all command-line based tool chains. It also permits the configuration of tool chain executables on a per-platform basis.

### Implementation

* Add `CommandLineToolInvocation` extends `org.gradle.nativebinaries.Tool` with read-write String property `executable`.
* Rename `GccTool` to `CommandLineTool` and change to have `withInvocation(Action<CommandLineToolInvocation>)` in place of `withArguments`
* Remove tool-specific getters from `Gcc`, and instead make `Gcc` serve as a NamedDomainObjectSet of `CommandLineTool` instances.
    * Continue to register a `CommandLineTool` for every supported language.
* Allow the `withInvocation` method to override the default executable to use.
* Add a sample, user-guide documentation and note the breaking change in the release notes.
* Consolidate various `ArgsTransformer` implementations so that most/all simply set/modify args on a `CommandLineToolInvocation`.

### User visible changes

    model {
        toolChains {
            gcc(Gcc) {
                cppCompiler.withInvocation {
                    args.replace("-m32", "-march=i386")
                }
                cCompiler.withInvocation {
                    executable "gcc-custom"
                }
                linker.withInvocation {
                    ...
                }
            }
            visualCpp(VisualCpp) {
                cppCompiler.withInvocation {
                   ...
                }
            }
        }
    }

### Test coverage

* Update existing test cases for argument tweaking with GCC
* Can use g++ instead of gcc for compiling C sources
* Can tweak arguments for VisualCpp and Clang

### Open issues

* Only to register a `CommandLineTool` for languages that are supported by build.
   * Need to make it easy to have centralised tool chain configuration that works regardless of languages in effect.
* Make it simpler to add support for new languages to existing tool chain implementations
