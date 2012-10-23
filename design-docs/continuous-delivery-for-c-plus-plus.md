
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

# Publishing and resolving dynamic libraries

## Use cases

A producer project produces a single dynamic library, for a single platform. The library depends on zero or more other dynamic libraries.
The library is published to a repository.

A consumer project resolves the library from the repository, and links an executable against it. Some libraries may be installed on the consumer
machine, and the remaining libraries are available in a repository.

Out of scope is the publishing of the resulting executable (this is a later story, below).

## Implementation

On some platforms, such as UNIX platforms, linking is done against the shared library binary. On Windows, linking is done
against the library's export file (`.lib`), which is created when the library is linked.

On most platforms, the name of the binary file is important, and at runtime must match the name that was used at link time. On UNIX platforms,
the so_name (or install_path on OS X) that is built into the library binary is used. If not present, the absolute path of the library binary file
is used. This means that in order to execute against a dynamic library.

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
* Define some standard artifact types for dynamic libraries, header archibes and export files.

# Publishing and resolving executables

## Use cases

A producer project produces a single executable, for a single platform. The executable depends on zero or more dynamic libraries.
The executable is published to a repository.

A consumer project resolves the executable from the repository, and executes it. Some libraries may be installed on the consumer machine, and the
remaining libraries are available in a repository.

## Implementation

On most platforms, executables must follow a certain plaform-specific convention. On UNIX platforms, for example, the executable must have the execute
permission set. On Windows platforms, the executable should have a `.exe` extension.

To implement this:

* There are a number of tasks in common with [publishing and resolving dynamic libraries](#shared-libraries) above.
* Producer project publishes the dependency meta-data for the executable.
* Consumer project uses correct names for resolved executables.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Consumer project installs the executable and libraries into a location where they are executable.
* Define some standard artifact types for executables.

# Binaries built for multiple Intel x86 architectures

## Use cases

A producer project compiles and links a dynamic library for multiple Intel x86 architectures, for a single operating system. The library has zero or more dependencies.
The library is published to a repository.

A consumer project links and runs an executable against the appropriate variant of the library, resolved from the repository.

Out of scope for this work is support for other chipsets, or projects the build a library for multiple chipsets.

## Implementation

There are 2 main parts to the architecture that we are interested in: The CPU instruction set that is being used, and the data model. Usually,
but not always, a 32-bit processor instruction set is used with 32-bit data model, and a 64-bit processor instruction set is used with 64-bit
data model.

Usually, it is possible to combine different instruction sets in the same binary. So, when linking a binary targetting the amb64 CPU, it is fine to link
against a library built for the x86 CPU. It is not possible to combine different data models in the same executable.

On OS X, a binary may target multiple architectures, as a universal binary. It should be noted that a universal binary simply supports more than one
architecture, but not necessarily every architecture as the name suggests. For example, a universal binary might include x86 & amd64 suport but no
ppc or ppc64 support.

File names are important here. Generally, architecture is not encoded in the binary file name. The build must be able to distinguish between binaries
that have the same file name (and install path) but are built for different architectures.

To implement this:

* Add appropriate tasks so that producer project can compile, link and publish the binaries for all architectures in a single build invocation.
* Add some mechanism for the developer to select the architectures they are interested in from the command-line and tooling API.
* Include in the published meta-data information about which (cpu + data model) each binary was built for.
* Consumer project selects the binaries with the appropriate cpu + data model when resolving the link and runtime dependencies for the executable.
* Allow compiler and linker settings to be specified for each architecture.
* Allow resolved binaries to have the same file name across architectures. For example, a dynamic library should be called libsomething.so regardless
  of whether it happens to be built for the x86 or amd64 architectures.
* Define some standard names for CPU instruction sets and architectures, plus mappings to platform specific names.
* Define some default architectures for the x86 chipset. So, every c++ binary may be built for the x86 and amd64 architectures by default.

To generate the binaries:

* For windows, run the 'x86' or 'x86_amd64' compiler.
* For linux + intel CPU, run with gcc with -m32 or -m64.
* For mac, run gcc with -arch i386, -arch x86_64, -arch ppc, -arch ppc64. Can include multiple times to generate a universal binary.

Native architecture names:

* OS X: i386, x86_64, ppc, ppc64

# Binaries built for multiple operating systems

## Use cases

A producer project compiles, links and publishes a dynamic library for multiple combinations of operating system and architecture. The library depends on zero or
more dynamic libraries. The library is published to a repository.

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

Producer project publishes a dynamic library with both debug and release variants.

Consumer project compiles, links and debugs an executable against this library.

## Implementation

Implementation-wise, this problem is similar in some respects to handling multiple architectures.

Usually, it is fine to link a release build of a libary into a debug binary, if the debug variant is not available. It is not fine to link a debug
build of a library into a release binary.

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

Consumer project compiles, links and publishes a dynamic library that includes the shared library.

## Implementation

To implement this:

* Add tasks to the publisher project to allow both static and dynamic library binaries to be built and published.
* Include in the published meta-data, information about which static libraries are linked statically into the binary.
* Allow compiler and linker settings to be specified separately for static and dynamic binaries.
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

# Open issues

* General purpose tree artifact.
* Handling for system libraries.
* Building for multiple chipsets.
* Selecting a compatible architecture at resolve time. For example, if I'm building for the amd64 cpu, I can use an x86 cpu + 64 bit data model.
* Selecting a compatible operating system version at resolve time.
* Selecting a compatible release binary when the debug binaries are not available.
* Need to include meta-data about which runtime a DLL is linked against. Same for dynamic libraries, but less common.
* Need to include meta-data about which optimisation level a binary is compiled for.
* Cross compilation.
* Custom variants.
* Calling convention.
