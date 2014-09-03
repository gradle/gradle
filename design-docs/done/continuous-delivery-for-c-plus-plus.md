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
