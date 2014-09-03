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
