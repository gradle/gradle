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

# Story: Introduce native binaries

This story introduces domain objects to represent each native binary that is built, sharing the concepts introduced in the new JVM language DSL.
This separates the physical binary from the logical component that it is built for. This will be used in later stories to handle building multiple
binaries for a native application or library.

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

## User visible changes

For this story, a read-only view of the binaries is available:

    apply plugin: 'java'
    apply plugin: 'cpp-exe'
    apply plugin: 'cpp-lib'

    assert binaries.mainClasses instanceof ClassDirectoryBinary
    assert binaries.mainExecutable instanceof ExecutableBinary
    assert binaries.mainSharedLibrary instanceof SharedLibraryBinary

Running `gradle mainExecutable` will build the main executable binary.

## Test cases

- Can apply the `java`, `cpp-exe` and `cpp-lib` plugins together, as shown in the example above.

## Open issues

- Change the native model to use deferred configuration.
- Add output file to binaries. Remove output file from the component.
- Add a convention to give each binary a separate output directory.
- Add windows and linux specific output files (eg .lib file for a shared library on windows).
- Allow `ExecutableBinary` and `SharedLibraryBinary` instances to be added manually.

# Story: Separate C++ compilation and linking of binaries

This story separates C++ compilation and linking of binaries into separate tasks, so that 1) object files built from other languages can be linked into a binary, and
2) so that the object files can be consumed in different ways, such as assembling into a static library.

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
- Add these task types to the DSL reference and mark as incubating.

## Open issues

- Naming scheme for tasks and binaries.
- Introduce a toolchain concept, so that the compiler and linker from the same toolchain are always used together.
- Add compiler and linker options to the binaries.
- Separate out compiler and linker options on the component.
- Add object file directory property to the binaries.
- Add a convention to give each binary a separate object file directory.
- Add link dependencies to binaries. This is resolved to determine which dependencies to link against.
- Add a hook to allow the generated compiler and linker command-line options to be tweaked before forking.
- Add an `InstallExecutable` task type and use this to install an executable.
- Allow the C++ compiler and linker executables for a toolchain to be specified.

## Test cases

- Incremental build:
    - Changing a compile option causes source files to be compiled and binary to be linked.
    - Changing a link option causes the binary to be linked but does not recompile source files.
    - Changing a comment in a source file causes the source file to be compiled by the but does not relink the binary.

# Story: Build a static library binary

This story introduces the concept of a static library binary that can be build for a C++ library.

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
    - Don't use any shared library flags (`-shared`, `-fPIC`) when compiling source files for a static library.
    - Use `ar` to assemble the static library.

## User visible changes

Given:

    apply plugin: `cpp-lib`

Running `gradle mainStaticLibrary` will build the main static library.

## Open issues

- Add output file and object file properties to static binaries.
- Allow `StaticLibraryBinary` instances to be added manually.
- Need to consume locally and between projects and between builds.
- Need separate compiler and linker options for building shared and static library.
- Need shared compiler and linker options for building shared and static library.
- Can in theory share the compile task between a static library and an executable built from the same source.

# Compile C source files using the C compiler

This story adds support for C source files as inputs to native binaries.

- Add `c` and `c++` source set types and allow these to be attached as inputs to a native binary.
- Add a `CCompile` task type.
- Change the `cpp` plugin to add a `CppCompile` instance for each C++ source set added to a native binary.
- Change the `cpp` plugin to add a `CCompile` instance for each C source set added to a native binary.
- Change the visual C++ toolchain to:
    - Use `/TC` to force source language to C for all source files in a `c` source set.
    - Don't use C++ options when compiling C source files.
    - Use `/TP` to force source language to C++ for all source files in a `c++` source set.
- Change the GCC toolchain to:
    - Use `gcc` to compile all source files in a `c` source set. Should also use `-x c` to force source language to C.
    - Use `g++` to compile all source files in a `c++` source set. Should also use `-x c++` to force source language to C++.
- Invoke the C compiler for all source files in a `c` source set type, and the C++ compiler for all source files in a `cpp` source set type.

## Open issues

- Need a 'cross-platform' and 'platform-specific' source set.
- Add input source sets to binaries.
- Move source sets from `cpp.sourceSets` container to `source` container.
- Add a convention for C and C++ source directories.
- Add compile dependencies to each source set.
- Add link dependencies to each source set, use these to infer the link dependencies of the binary.
- Should probably not use `g++` to link when there is no C++ source included in a binary.
- Must use the C compiler and C++ compiler from the same toolchain for a given binary.
- Need separate compiler options for C and C++.
- Need shared compiler options for C and C++.
- Need to manually define C++ and C source sets.
- Need to compose assembler/C/C++ source sets.
- Allow the C compiler executable for a toolchain to be specified.

## Test cases

- mixed C/C++ binary.
- each type of binary.

# Build a binary from assembler source files

This story adds support for using assembler source files as inputs to a native binary.

- Add an `assembler` source set type and allow these to be added to a native binary.
- Add an `Assemble` task type.
- Change the `cpp` plugin to add an `Assemble` instance for each assembler source set added to a native binary.
- Change the visual C++ toolchain to:
    - Use `ml /nologo /c` to assemble a source file to an object file.
- Change the GCC toolchain to:
    - Use `as` to assemble a source file to an object file.

## Open issues

- Different source files by platform
- Extract an assembler and a binaries plugin
- Add a convention for assembler source directories.
- Should possibly use `ld` instead of `gcc` or `g++` to link the binaries.
- Must use the assembler, C and C++ compiler from the same toolchain for a given binary.
- Need assembler options.
- Need to manually define assembler source sets.
- Allow the assembler executable for a toolchain to be specified.

## Test cases

- mixed C/C++/ASM binary.
- each kind of binary.

# Build different variants of a native component

This story adds support for building multiple variants of a native component. For each variant of a component, a binary of the
appropriate type is defined.

## Open issues

- Need to be able to build a single variant or all variants.
- Need separate compiler, linker and assembler options for each variant.
- Need shared compiler, linker and assembler options for all variants.
- Need to consume locally and between projects and between builds.
- Need to infer the default variant.

# Build a native component using multiple tool chains

This story adds support for building a native component using multiple tool chains. Each variant may have a tool chain associated with it.

## Open issues

- Need to be able to build for a single toolchain or all available toolchains.
- Need separate compiler, linker and assembler options for each toolchain.
- Need shared compiler, linker and assembler options for all toolchains.
- Need to consume locally and between projects and between builds.
- Need to discover the available tool chains.

## Tests

- Build on windows using visual c++ and gcc.

# Build a native component for multiple architectures

This story adds support for building a component for multiple architectures. Introduce the concept of a platform, and each variant may have a platform associated
with it.

## Open issues

- Need to be able to build for a single architecture or all available architectures.
- Need to discover which architectures a tool chain can build for.
- Need separate compiler, linker and assembler options for each platform.
- Infer the default platform and architecture.
- Define some conventions for architecture names.

## Tests

- Cross compile a 32-bit binary on a 64-bit linux machine.

# Cross-compile for multiple operating systems

This story adds support for cross-compilation. Add the concept of an operating system to the platform.

## Open issues

- Different source files by platform
- Need to be able to build for a single platform or all available platforms.
- Need separate compiler, linker and assembler options for each operating system.
- Need to discover which platforms a tool chain can build for.
- Define some conventions for operating system names.
- Add some opt-in way to define variants using a matrix of (flavour, tool chain, architecture, operating system).

# Publishing and resolving shared libraries

## Use cases

A producer project produces a single shared library, for a single platform. The library depends on zero or more other dynamic libraries.
The library is published to a repository.

A consumer project resolves the library from the repository, and links an executable against it. Some libraries may be installed on the consumer
machine, and the remaining libraries are available in a repository.

Out of scope is the publishing of the resulting executable (this is a later story, below).

## Implementation

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

# Publishing and resolving executables

## Use cases

A producer project produces a single executable, for a single platform. The executable depends on zero or more shared libraries.
The executable is published to a repository.

A consumer project resolves the executable from the repository, and executes it. Some libraries may be installed on the consumer machine, and the
remaining libraries are available in a repository.

## Implementation

On most platforms, executables must follow a certain plaform-specific convention. On UNIX platforms, for example, the executable must have the execute
permission set. On Windows platforms, the executable should have a `.exe` extension.

To implement this:

* There are a number of tasks in common with [publishing and resolving shared libraries](#shared-libraries) above.
* Producer project publishes the dependency meta-data for the executable.
* Consumer project uses correct names for resolved executables.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Consumer project installs the executable and libraries into a location where they are executable.
* Define some standard artifact types for executables.

# Binaries built for multiple Intel x86 architectures

## Use cases

A producer project compiles and links a shared library for multiple Intel x86 architectures, for a single operating system. The library has zero or
more dependencies. The library is published to a repository.

A consumer project links and runs an executable against the appropriate variant of the library, resolved from the repository.

Out of scope for this work is support for other chipsets, or projects that build a library for multiple chipsets.

## Implementation

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

# Binaries built for multiple operating systems

## Use cases

A producer project compiles, links and publishes a shared library for multiple combinations of operating system and architecture. The library depends
on zero or more shared libraries. The library is published to a repository.

A consumer project compiles, links and runs an executable against the libary.

Out of scope is cross compilation for other platforms, or building binaries for multiple versions of the same operating system.

## Implementation

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

# Header-only libraries

## Use case

Producer project publishes a library consisting of header files only (e.g. a library of C++ template classes).

Consumer project compiles an executable against this library.

## Implementation

To implement this:

* Allow zero or more binaries for a given library at link and runtime. Also allow for zero or more header files.

# Debug and release binaries

## Use case

Producer project publishes a shared library with both debug and release variants.

Consumer project compiles, links and debugs an executable against this library.

## Implementation

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

# Publishing and resolving static libraries

## Use cases

Publisher project publishes a shared library.

Consumer project compiles, links and publishes a shared library that includes the shared library.

## Implementation

To implement this:

* Add tasks to the publisher project to allow both static and shared library binaries to be built and published.
* Include in the published meta-data, information about which static libraries are linked statically into the binary.
* Consumer project selects either the static or dynamic binaries for a given dependency at link time. A dependency that is statically linked
  into a binary has no files that are required at runtime, but may have files that are required at debug time.

# Running tests

## Use cases

## Implementation

Generally, C++ test frameworks compile and link a test launcher executable, which is then run to execute the tests.

To implement this:
* Define a test source set and associated tasks to compile, link and run the test executable.
* It should be possible to run the tests for all architectures supported by the current machine.
* Generate the test launcher source and compile it into the executable.
* It would be nice to generate a test launcher that is integrated with Gradle's test eventing.
* It would be nice to generate a test launcher that runs test cases detected from the test source (as we do for JUnit and TestNG tests).

# Publishing multiple binaries from a project

TBD

# Building binaries for all operating systems in a single build

TBD

# Incremental compilation for C and C++

## Implementation

There are two approaches to extracting the dependency information from source files: parse the source files looking for `#include` directives, or
use the toolchain's preprocessor.

For Visual studio, can run with `/P` and parse the resulting `.i` file to extract `#line nnn "filename"` directives. In practise, this is pretty slow.
For example, a simple source file that includes `Windows.h` generates 2.7Mb in text output.

For GCC, can run with `-M` or `-MM` and parse the resulting make file to extract the header dependencies.

The implementation will also remove stale object files.

# Expose only public header files for a library

TBD

# Including Windows resource files in binaries

Resource files can be linked into a binary.

## Implementation

* Resource files are compiled using `rc` to a `.res` file, which can then be linked into the binary.
* Add a `resource` source set type and allow these to be attached to a binary.
* Add the appropriate tasks to compile resource scripts.

# Using Windows linker def files

## Implementation

* A `.def` file lists `__stdcall` functions to export from a DLL. Can also use `__declspec(dllexport)` in source to export a function.
* Functions are imported from a DLL using `__declspec(dllimport)`.

# Using Windows IDL files

## Implementation

* Use `midl` to generate server, client and header source files.

# Visual studio integration

## Implementation

* Allow the visual studio project file to be generated.
* Merge with existing project file, as for IDEA and Eclipse.
* Add hooks for customising the generated XML.

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
