This document describes a number of improvements to allow C++ projects to be built, tested, published are shared between teams.

# Current state

Currently, the Gradle C++ plugins can compile and link multiple executables and shared libraries. Dependency management is partially implemented, so
that the binaries can be published and resolved in some basic form. There are various missing pieces. For example, dependency meta-data is not
published, and resolved binaries have the incorrect names on some platforms.

## Some terminology

Given a C++ library or executable, we have:

### Producer project

The project that compiles, links and publishes the binary to be shared. This sharing may happen within the same build or across several different builds
via a repository.

### Consumer project

A project that uses the published binary in some form. In the case of a published library, the consumer usually installs, links against the library,
and runs the result. In the case of a published executable, the consumer generally installs and runs the executable.

### Native binary

A binary that runs on a particular native C runtime. This refers to the physical binary file.

### Executable binary

An executable native binary.

### Shared library binary

A library binary that is linked into an executable or shared library at runtime

### Static library binary

A library binary that is linked into an executable or shared library at link time.

### Native component

A software component that runs on the native C runtime. This refers to the logical entity, rather than the physical.

### Native application

A native component that represents an application.

### Native library

A native component that represents a library to be used in other native components.

# Milestone 1

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

## Story: Build a static library binary

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

## Story: Allow customisation of binaries before and after linking

This story introduces a lifecycle task for each binary, to allow tasks to be wired into the task graph for a given binary. These tasks will be able to modify the object files or binary files before they are consumed.

- Change `Binary` to extend `Buildable`.
- Change `NativeComponent` so that it no longer extends `Buildable`.
- Change the binaries plugin to add a lifecycle task called `${binary.name}`.
- Change the cpp plugin to rename the link tasks for a binary to `link${binary.name}`.
- Change `DefaultClassDirectoryBinary` to implement `getBuildDependencies()` (it currently has an empty implementation).
- Change `CppPlugin` so that the link task for a binary uses the compile task's output as input, rather than depend on the compile task.
- Add an `InstallExecutable` task type and use this to install an executable.
- Change `CppPlugin` so that the install task for an executable uses the executable binary as input, rather than depend on the link task.

*Note*: There is a breaking change here, as the link task for a given binary has been renamed.

### User visible changes

    apply plugin: 'cpp-exe'
    
    task tweakExecutable {
        dependsOn linkMainExecutable
        doFirst {
            ["strip", linkMainExecutable.outputFile].execute()
        }
    }
    
    mainExecutable.dependsOn tweakExecutable

### Open issues

- Some convenience to wire this in?

## Story: Allow customisation of binary compilation and linking

This story allows some configuration of the settings used to compile and link a given binary.

Some initial support for settings that will be shared by all binaries of a component will also be added.

Later stories will add more flexible and convenient support for customisation of the settings for a binary.

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

### Open issues

- Preprocessor macros should be modelled as a map
- Add set methods for each of these properties.
- `NativeComponent.binaries` collides with `project.binaries`, for example, when defining binary libs.
- `NativeComponent.binaries.all { }` is awkward for linker settings, as these aren't available for a shared lib binary.
- Add back some conveniences for compiler and linker args for all binaries once delayed configuration is implemented.
- Allow customisation of the source sets for a binary.
- Strongly type the compiler and linker args as `String`.
- Need to run `ranlib` over static libraries.

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
- Add `Library.getShared()` and `getStatic()` methods which return `NativeDependencySet` imlementations for the shared and static variants of the library.
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
    - Use a (static, shared) library that depends on another (static, shared) library.
    - In each case, verify that only shared libraries are included in the install image.
    - In each case, remove the original binaries before running the install image.
- A dependency on a binary overrides a dependency on the library that produced the binary.

### Open issues

- Need a new name for `NativeDependencySet`.
- Need some way to convert a `NativeDependencySet` to a read-only library.
- Need to apply a lifecycle to the resolved libs of `CppSourceSet` and `NativeBinary`.
- Improve consumption of libraries from other projects.
- Some mechanism to use the static binary as default for a library.
- Some mechanism to select static or dynamic linkage for each dependency of a binary.
- Infer incoming libraries from component dependencies.
- Samples that demonstrates how to link a static library into an executable.
- Move Component and Binary interfaces to new packages.
- Add some way to create ad hoc `NativeDependencySet` instances, for example, a library produced by another build tool.
- Need to be able to fine-tune compile time, link time and runtime inputs.
- Merge `CppSourceSet.lib()` and `CppSourceSet.dependency()`.
- Allow a `Binary` to be attached to a publication.
- Update publication so that a binary's include, link and runtime files are published.
- Need to be able to deal with the fact that only position-independent binaries can be linked into position-independent binaries
    - Make it possible to build a position-independent variant of a static library binary
    - Add the '-fPIC' flag when compiling to ensure that the static library can be included in a shared library
    - Change dependency resolution to choose the position-indepenent variant of a static library when linking into a shared library

## Story: Defer creation of binaries

This story defers creation of the binaries for a native component until after the component has been fully configured. This will be used in later stories to allow
different variants to be defined for a component.

- Add an internal (for now) `ConfigurationActionContainer` interface, with a single `onConfigure(Runnable)` method.
- Add a project scoped implementation of this interface. The `onConfigure()` method is invoked just before the `afterEvaluated` event.
- Change the publishing plugin to register an action that configures the publications of the project.
- Change the handling of `@DeferredConfigurable` so that a `@DeferredConfigurable` is not automatically configured during project configuration. Instead, it is configured
  on demand.
- Change the C++ plugin to register an action that configures the components of the project and then creates the binaries of each component.

*Note*: this story introduces some breaking changes, as the binaries and their tasks will not be defined when the build script is executed. Later stories will
improve this.

### User visible changes

    apply plugin: 'cpp-lib'
    
    libraries {
        main {
            // Defaults for all binaries for this library
            binaries.all {
                …
            }
            // Defaults for all shared library binaries for this library
            binaries.withType(type: SharedLibraryBinary) {
                …
            }
            // Note: These will not work as expected
            binaries.each { … }
            binaries.find { … }
            binaries.size()
        }
    }
    
    binaries.all {
        … 
    }
    
    binaries.withType(NativeBinary) {
        …
    }
    
    // Note: this will not work as previously. It will fail because the binary does not exist yet
    binaries {
        mainSharedLibrary { … }
    }

    //Note: this will not work as previously. It will fail because the task does not exist yet
    compileMainSharedLibrary { … }


### Open issues

- Introduce a `DomainObjectCollection` that uses delayed configuration of its elements.
- Introduce a `NamedDomainObjectCollection` that uses delayed configuration of its elements.
- Warn when using binaries container in a way that won't work.
- Warn when mutating component after binaries have been created.

## Story: Build different variants of a native component

This story adds initial support for building multiple variants of a native component. For each variant of a component, a binary of the appropriate type is defined.

- Add a `flavors` container to `NativeComponent`
- If no flavors are defined, then implicity define a `default` flavor (or perhaps just always define the `default` flavor).
- For each flavor defined for an `Executable`, create an `ExecutableBinary`.
- For each flavor defined for a `Library`, create a `SharedLibraryBinary` and `StaticLibraryBinary`.
- Add a property to `NativeBinary` to allow navigation to the flavor for this binary.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`, select the binary of `l` with the same flavor as `b`, if present, otherwise
  select the binary of `l` with flavor `default`, if present, otherwise fail (will need to rework `NativeDependencySet` to make this work)
- Add an `assemble` lifecycle task for each component.
- Running `gradle assemble` should build all binaries for the main executable or library
- Running `gradle uploadArchives` should build and publish all binaries for the main executable or library

### User visible changes

    libraries {
        main {
            flavors {
                main
                withOptionalFeature
            }
            binaries.matching { flavor == flavors.main } {
                compilerArgs '-DSOME_FEATURE
            }
        }
    }

This will define 4 binaries:

- library: 'main', flavor: 'main', packaging: 'static'
- library: 'main', flavor: 'main', packaging: 'shared'
- library: 'main', flavor: 'withOptionalFeature', packaging: 'static'
- library: 'main', flavor: 'withOptionalFeature', packaging: 'shared'

### Open issues

- Add a 'development' assemble task, which chooses a single binary for each component.
- Need to make standard 'build', 'check' lifecycle tasks available too.
- Formalise the concept of a naming scheme for binary names, tasks and file paths.
- Add a convention to give each binary a separate output directory (as the name of each variant binary can be the same).
- Add a convention to give each binary a separate object file directory.
- Need to be able to build a single variant or all variants.
- Need separate compiler, linker and assembler options for each variant.
- Need shared compiler, linker and assembler options for all variants.
- Need to consume locally and between projects and between builds.
- Need to infer the default variant.
- Need to handle dependencies.
- Need to publish all variants.

## Story: Ensure CI builds exercise test coverage for supported tool chains

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

### Open issues

- Use rules to imply the type of each child of a functional source set.
- Define a functional source set for each component.
- Separate C++ header file source set type.
- Need to configure each component and source set lazily.
- Need to deal with source sets that are generated.
- Need a `CppSourceSet.getBuildDependencies()` implementation.

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

### Open issues

- Need a 'cross-platform' and 'platform-specific' source set.
- Add a convention for C and C++ source directories.
- Add compile dependencies to each source set.
- Add link dependencies to each source set, use these to infer the link dependencies of the binary.
- Should probably not use `g++` to link when there is no C++ source included in a binary.
- Need separate compiler options for C and C++.
- Need shared compiler options for C and C++.
- Need to manually define C++ and C source sets.
- Need to compose assembler/C/C++ source sets.
- The "by-convention" plugins need to add "main" C and headers source sets.
- Change `NativeDependencySet` to handle separate C and C++ headers.
- Rework the plugins so that there is something like:
    - A `cpp-lang` plugin, which adds support for C++ source sets and compiling them to object files.
    - A `c-lang` plugin, which adds support for C source sets and compiling them to object files
    - An `assembler-lang` plugin, which adds support for assembler source sets and compiling them to object files.
    - A `native-binaries` plugin, which adds the base support for native binaries and components.
    - A `native-library` and `native-application` plugin, which adds by-convention support for a main library or main application.

### Test cases

- Build breaks when C++ source file cannot be compiled.
- Build breaks when C source file cannot be compiled.
- Build breaks when a binary cannot be linked, for each type of binary.
- C compilation is incremental wrt C source files, header files and compiler settings.
- Mixed C/C++ binary, for each type of binary.
- Project has mixed C and C++ header files.

## Story: Build a binary from assembler source files

This story adds support for using assembler source files as inputs to a native binary.

- Add an `AssemblerSourceSet` type and allow these to be added to a native binary.
- Add an `Assemble` task type.
- Change the `cpp` plugin to add an `Assemble` instance for each assembler source set added to a native binary.
- Change the visual C++ toolchain to:
    - Use `ml /nologo /c` to assemble a source file to an object file.
- Change the GCC toolchain to:
    - Use `as` to assemble a source file to an object file.

### Open issues

- Different source files by platform
- Extract an assembler and a binaries plugin
- Add a convention for assembler source directories.
- Should possibly use `ld` instead of `gcc` or `g++` to link the binaries.
- Must use the assembler, C and C++ compiler from the same toolchain for a given binary.
- Need assembler options on binary.
- Need to manually define assembler source sets.

### Test cases

- Build breaks when source file cannot be assembled.
- Mixed C/C++/ASM binary, for each kind of binary.
- A project can have all C, C++ source and header files and assembly source files in the same source directory.

# Milestone 2

## Story: Allow library binaries to be used as input to other libraries

This story add support for using a library which has dependencies on other libraries.

Given a library `a` that uses another library `b` as input:

- When compiling a binary that uses library `a`, then include the exported headers for library `a` but not the headers for library `b`.
- The link-time files of a shared library `a`, are the link binaries for shared library `a` only (the `.so` or the `.lib` file).
- The link-time files of a static library `a`, are the link binaries for static library `a` (the `.a` or `.lib` file) and the link-time files of its direct dependencies.
- The runtime files of a shared library `a`, are the runtime binaries for shared library `a` (the `.so` or the `.dll` file) and the runtime files of its direct dependencies.
- The runtime files of a static library `a`, are the runtime files of its direct dependencies.

- Change the native model to use deferred configuration:
    - Configure a source set completely before using it as input to a component or binary.
    - Configure a component completely before using it as a source set dependency or defining its binaries.
    - Configure a binary completely before defining tasks for it.
    - Configure a component completely before defining tasks for it.

### Open issues

- Need to apply conflict resolution as we can't include the static and shared binaries for a given library at link time.

## Story: Simplify configuration of a binary based on its properties

- Add a `DomainObjectCollection` sub-interface with method `all(map, action)`. This method is equivalent to calling `matching(predicate, action)`, where `predicate` is a spec that selects all objects whose property values equal those specified in the map. This method can be used to do delayed configuration of the elements of the collection.
- Change `NativeComponent.binaries` and `BinariesContainer` to provide implementations of this interface.

### User visible changes

    apply plugin: 'cpp-lib'

    libraries {
        main {
            // Defaults for all binaries for this library
            binaries.all {
                …
            }
            // Defaults for all shared library binaries for this library
            binaries.all(type: SharedLibraryBinary) {
                …
            }
            // Defaults for all binaries built by Visual C++
            binaries.all(toolchain: toolchains.visualCpp) {
                …
            }
            // Defaults for all shared library binaries built by Visual C++
            binaries.all(toolchain: toolchains.visualCpp, type: SharedLibraryBinary) {
                …
            }
        }
    }

    binaries.all(name: 'mainSharedLibrary') {
        …
    }

    binaries.all(type: NativeBinary) {
        …
    }

### Open issues

- Roll out the predicate methods to other containers.
- Need to deal with things like 'all versions of visual C++', 'all toolchains except visual C++', 'visual C++ 9 and later', 'gcc 3' etc.
- Need to deal with things like 'all unix environments', 'all windows environments', etc.

## Story: Convenient configuration of tasks for binaries and components

This story defers configuration of the tasks for a binary or component until after the object has been fully configured.

- Change the C++ plugin to create the tasks for each native binary after the binary has been configured.
- Change the C++ plugin to create the tasks for each component after the tasks for the binary have been configured.
- Remove the convention mappings from the C++ plugin.

### Open issues

- Allow navigation from a `Binary` to the tasks associated with the binary.
- Some way to configure the tasks for a binary and publication.

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
- Change a header file that is included by another header file.
- Change a compiler setting, all source files are recompiled.
- Remove a source file, the corresponding object file is removed.
- Rename a source file, the corresponding object file is removed.
- Remove all source files, all object files and binary files are removed.

## Story: Allow a binary to be defined that is not part of a component

- Allow native binary instances to be added manually.
- Change `NativeBinary` so that not every binary has an associated component.

### Test cases

- Can define a standalone executable, shared library or static library binary, and the appropriate tasks are created and configured appropriately.
- Can define and configure standalone compile and link tasks.

## Story: Build a native component using multiple tool chains

This story adds support for building a native component using multiple tool chains. Each variant may have a tool chain associated with it.

### Open issues

- Reasonable behaviour when no tool chains are available on the current machine.
- Allow the C++ compiler executable for a toolchain to be specified.
- Allow the C compiler executable for a toolchain to be specified.
- Allow the linker and library archiver for a toolchain to be specified.
- Allow the assembler for a toolchain to be specified.
- Need to be able to build for a single toolchain or all available toolchains.
- Need separate compiler, linker and assembler options for each toolchain.
- Need shared compiler, linker and assembler options for all toolchains.
- Need to consume locally and between projects and between builds.
- Need to discover the available tool chains.

### Tests

- Build on windows using visual c++ and gcc.

## Story: Build a native component for multiple architectures

This story adds support for building a component for multiple architectures. Introduce the concept of a platform, and each variant may have a platform associated
with it.

### Open issues

- Need to be able to build for a single architecture or all available architectures.
- Need to discover which architectures a tool chain can build for.
- Need separate compiler, linker and assembler options for each platform.
- Infer the default platform and architecture.
- Define some conventions for architecture names.

### Tests

- Cross compile a 32-bit binary on a 64-bit linux machine.

## Story: Cross-compile for multiple operating systems

This story adds support for cross-compilation. Add the concept of an operating system to the platform.

### Open issues

- Different source files by platform
- Need to be able to build for a single platform or all available platforms.
- Need separate compiler, linker and assembler options for each operating system.
- Need to discover which platforms a tool chain can build for.
- Define some conventions for operating system names.
- Add some opt-in way to define variants using a matrix of (flavour, tool chain, architecture, operating system).

## Story: Build debug and release variants of a native component 

TBD

## Story: Convenient configuration of compiler and linker settings

### Open issues

- Add a hook to allow the generated compiler and linker command-line options to be tweaked before forking.
- Add properties to set macros and include directories for a binary, allow these to be set as toolchain specific compiler args (ie -D and -I) as well.
- Add properties to set system libs and lib directories for a binary, allow these to be set as toolchain specific linker args (ie -l and -L) as well.
- Add set methods for each of these properties.
- Split the compiler and linker settings out to separate types.

## Story: Include Windows resource files in binaries

Resource files can be linked into a binary.

### Implementation

* Resource files are compiled using `rc` to a `.res` file, which can then be linked into the binary.
* Add a `resource` source set type and allow these to be attached to a binary.
* Add the appropriate tasks to compile resource scripts.

## Story: Use Windows linker def files

### Implementation

* A `.def` file lists `__stdcall` functions to export from a DLL. Can also use `__declspec(dllexport)` in source to export a function.
* Functions are imported from a DLL using `__declspec(dllimport)`.

## Story: Use Windows IDL files

### Implementation

* Use `midl` to generate server, client and header source files.

# Milestone 3

## Story: Build binaries against a library in another project

### Open issues

- When linking a native binary, link against exactly the same version of each library that we compiled against, plus any additional link-time dependencies (resources, for example).
- When installing a native executable, also install exactly the same versions of each library that we linked against, plus any additional runtime dependencies.

## Story: Support CUnit test execution

### Use cases

### Implementation

Generally, C++ test frameworks compile and link a test launcher executable, which is then run to execute the tests.

To implement this:
* Define a test source set and associated tasks to compile, link and run the test executable.
* It should be possible to run the tests for all architectures supported by the current machine.
* Generate the test launcher source and compile it into the executable.
* It would be nice to generate a test launcher that is integrated with Gradle's test eventing.
* It would be nice to generate a test launcher that runs test cases detected from the test source (as we do for JUnit and TestNG tests).

### Open issues

* Need a `unitTest` lifecycle task, plus a test execution task for each variant of the unit tests.
* Unit test executable needs to link with the object files that would be linked into the main executable.
* Need to exclude the `main` method.

## Story: Expose only public header files for a library

TBD

## Story: Visual studio integration

### Implementation

* Allow the visual studio project file to be generated.
* Merge with existing project file, as for IDEA and Eclipse.
* Add hooks for customising the generated XML.

# Milestone 4

## Story: Publish and resolve shared libraries

### Use cases

A producer project produces a single shared library, for a single platform. The library depends on zero or more other dynamic libraries.
The library is published to a repository.

A consumer project resolves the library from the repository, and links an executable against it. Some libraries may be installed on the consumer
machine, and the remaining libraries are available in a repository.

Out of scope is the publishing of the resulting executable (this is a later story, below).

### Implementation

On some platforms, such as UNIX platforms, linking is done against the shared library binary. On Windows, linking is done
against the library's export file (`.lib`), which is created when the library is linked.

On most platforms, the name of the binary file is important, and at runtime must match the name that was used at link time. On UNIX platforms,
the so_name (or install_path on OS X) that is built into the library binary is used. If not present, the absolute path of the library binary file
is used. This means that in order to execute against a shared library.

Generally, this means that the library must be installed in some form before it can be linked against.

To implement this story:

* Producer project publishes the dependency meta-data for the library.
* Producer project published the library's export file (.lib) when building on Windows.
* Consumer project uses correct names for resolved libraries.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Separate out compile, link and runtime dependencies, so that different artifacts can be used at compile, link and runtime.
* Consumer project installs the libraries into a location where the executable can find them, with their correct names, and uses these locations
  at link time.
* Consumer determine which libraries are already installed on the machine, and uses these from their installed location at link time.
* Define some standard artifact types for shared libraries, header archives and export files.

## Story: Publish and resolve executables

### Use cases

A producer project produces a single executable, for a single platform. The executable depends on zero or more shared libraries.
The executable is published to a repository.

A consumer project resolves the executable from the repository, and executes it. Some libraries may be installed on the consumer machine, and the
remaining libraries are available in a repository.

### Implementation

On most platforms, executables must follow a certain platform-specific convention. On UNIX platforms, for example, the executable must have the execute
permission set. On Windows platforms, the executable should have a `.exe` extension.

To implement this:

* There are a number of tasks in common with [publishing and resolving shared libraries](#shared-libraries) above.
* Producer project publishes the dependency meta-data for the executable.
* Consumer project uses correct names for resolved executables.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Consumer project installs the executable and libraries into a location where they are executable.
* Define some standard artifact types for executables.

## Story: Binaries built for multiple Intel x86 architectures

### Use cases

A producer project compiles and links a shared library for multiple Intel x86 architectures, for a single operating system. The library has zero or
more dependencies. The library is published to a repository.

A consumer project links and runs an executable against the appropriate variant of the library, resolved from the repository.

Out of scope for this work is support for other chipsets, or projects that build a library for multiple chipsets.

### Implementation

There are 2 main parts to the architecture that we are interested in: The CPU instruction set that is being used, and the data model. Usually,
but not always, a 32-bit processor instruction set is used with 32-bit data model, and a 64-bit processor instruction set is used with 64-bit
data model.

Usually, it is possible to combine different instruction sets in the same binary. So, when linking a binary targetting the amd64 CPU, it is fine to link
against a library built for the x86 CPU. It is not possible to combine different data models in the same executable.

On OS X, a binary may target multiple architectures, as a universal binary. It should be noted that a universal binary simply supports more than one
architectures, but not necessarily every architecture as the name suggests. For example, a universal binary might include x86 & amd64 suport but no
ppc or ppc64 support.

File names are important here. Generally, architecture is not encoded in the binary file name. The build must be able to distinguish between binaries
that have the same file name (and install path) but are built for different architectures.

It is not always possible to build the binaries for more than one architecture on a given machine. On Linux machines, the system libraries for the
target architecture must be installed and available for linking. On Windows machines, a separate compiler must be used for each architecture.

To implement this:

* Add appropriate tasks so that producer project can compile, link and publish the binaries for all available architectures in a single build invocation.
* Add some mechanism for the developer to select the architectures they are interested in from the command-line and tooling API.
* Include in the published meta-data information about which (CPU + data model) each binary was built for.
* Consumer project selects the binaries with the appropriate (CPU + data model) when resolving the link and runtime dependencies for the executable.
* Allow compiler and linker settings to be specified for each architecture.
* Allow resolved binaries to have the same file name across architectures. For example, a shared library should be called `libsomething.so` regardless
  of whether it happens to be built for the x86 or amd64 architectures.
* Define some standard names for CPU instruction sets and architectures, plus mappings to platform specific names.
* Define some default architectures for the x86 chipset. So, every C++ binary may be built for the x86 and amd64 architectures by default.

To generate the binaries:

* For windows, run the 'x86' or 'x86_amd64' compiler.
* For linux + intel CPU, run with gcc with -m32 or -m64.
* For OS X, run gcc with -arch i386, -arch x86_64, -arch ppc, -arch ppc64. Can include multiple times to generate a universal binary. Need to fix
  GRADLE-2431.

Native architecture names:

* OS X: i386, x86_64, ppc, ppc64

## Story: Binaries built for multiple operating systems

### Use cases

A producer project compiles, links and publishes a shared library for multiple combinations of operating system and architecture. The library depends
on zero or more shared libraries. The library is published to a repository.

A consumer project compiles, links and runs an executable against the libary.

Out of scope is cross compilation for other platforms, or building binaries for multiple versions of the same operating system.

### Implementation

Generally, a given machine will not be able to build the binaries for all target operating systems. This means that multiple coordinated builds will
be involved.

For the purposes of this story, this coordination will be manual. A human or CI server will trigger a number of builds, each of which builds a subset of
the binaries and uploads them. Finally, a build that uploads the meta-data and other operating system independent binaries (header, jars, source and so on) will
be triggered. See [this story](#single-build) for a description of how this will be improved.

This multi-build approach will also be used to allow binaries for multiple chipsets, and for multiple operating system versions, to be built. There are
currently no stories for these use cases.

There are two aspects of operating system that we are interested in: the operating system + version. The version is really a proxy for other attributes
we don't model yet: system calling convention, ABI version, language runtime version, which system APIs are available, and so on.

File names are important here. Often, the platform is not encoded int the file name. The build must handle binaries that have the same name (and
install path) but are built for different operating systems.

To implement this:

* Add appropriate tasks so that the producer project can compile, link and publish the binaries for the current machine's operating system.
* Allow the binaries for a given version of the library to be built and published from multiple machines.
* Include in the published meta-data information about which operating system + version each binary was built for.
* Consumer project selects the binaries for the appropriate operating system when resolving link and runtime dependencies for the executable.
* Allow compiler and linker settings to be specified for each operating system.
* Allow dependencies to be declared for each operating system.
* Allow the producer to declare a dependency on operating system version (or range, more likely).
* Allow resolved binaries to have the same file name across operating system.
* Define some standard operating system names.

## Story: Header-only libraries

### Use case

Producer project publishes a library consisting of header files only (e.g. a library of C++ template classes).

Consumer project compiles an executable against this library.

### Implementation

To implement this:

* Allow zero or more binaries for a given library at link and runtime. Also allow for zero or more header files.

## Story: Debug and release binaries

### Use case

Producer project publishes a shared library with both debug and release variants.

Consumer project compiles, links and debugs an executable against this library.

### Implementation

Implementation-wise, this problem is similar in some respects to handling multiple architectures.

Usually, it is fine to link a release build of a libary into a debug binary, if the debug variant is not available. It is not fine to link a debug
build of a library into a release binary. On Windows, it is not ok to link a release build of a library into a debug binary.

On some platforms, additional artifacts are required to debug a binary. On gcc based platforms, the debug information is linked into the binary.
On Windows, the debug information is contained in a separate program database file (`.pdb`).

On all platforms, the source files for the binary are required.

To implement this:

* Producer project publishes the program database file (.pdb) for Windows binaries.
* Producer project publishes the source files for the library.
* Include in the published meta-data, information about whether the binary is a release or debug binary.
* Separate out debug time dependencies from runtime.
* Consumer project selects the approprate release or debug library binraries when resolving link, execute and debug dependencies.
* Consumer project installs the pdb and source files into the appropriate locations, so the debugger can find them.
* Allow compiler and linker settings to be specified separately for release and debug binaries.
* Define a set of default build types (debug and release, say).
* Allow the producer and consumer projects to define custom build types.

To generate the release binaries (default):
* Enable conservative optimisations.
* Compile with -DNDEBUG
* Compile with -g0

To generate the debug binaries (default):
* Disable optimisation.
* Compile with -g or /Zi
* Link with /DEBUG

## Story: Publish and resolve static libraries

### Use cases

Publisher project publishes a shared library.

Consumer project compiles, links and publishes a shared library that includes the shared library.

### Implementation

To implement this:

* Add tasks to the publisher project to allow both static and shared library binaries to be built and published.
* Include in the published meta-data, information about which static libraries are linked statically into the binary.
* Consumer project selects either the static or dynamic binaries for a given dependency at link time. A dependency that is statically linked
  into a binary has no files that are required at runtime, but may have files that are required at debug time.

## Story: Publish multiple binaries from a project

TBD

## Story: Build binaries for all operating systems in a single build

TBD

# Open issues

* General purpose tree artifact.
* Handling for system libraries.
* Building for multiple chipsets.
* Selecting a compatible architecture at resolve time. For example, if I'm building for the amd64 cpu, I can use an x86 cpu + 64 bit data model.
* Selecting a compatible operating system version at resolve time.
* Selecting a compatible release binary when the debug binaries are not available.
* Need to include meta-data about which runtime an DLL is linked against. Same for UNIX shared libraries, but less common.
* Need to include meta-data about which optimisation level a binary is compiled for.
* Cross compilation.
* Custom variants.
* Calling convention.
* Can in theory share the compile task between a static library and an executable built from the same source.
* Publishing and resolving RPM/DEB/NuGet/pkg-config/ etc.
* Support for profiling builds: build with profiling enabled, execute a bunch of times, then build again using the profiling information.
