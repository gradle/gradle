
This document describes a number of improvements to allow C++ projects to be built, tested, published are shared between teams.

* [Publishing and resolving dynamic libraries](#shared-libraries)
* [Publishing and resolving executables](#executables)
* [Binaries built for multiple Intel x86 architectures](#multiple-architectures)
* [Binaries built for multiple operating systems](#multiple-operating-systems)
* [Header-only libraries](#header-only-libraries)
* [Debug and release binaries](#debug-and-release-binaries)
* [Building static libraries](#static-libraries)
* [Running tests](#tests)
* [Publishing multiple binaries from a project](#multiple-binaries)

# Current state

Currently, the Gradle C++ plugins can compile and link multiple executables and shared libraries. Dependency management is partially implemented, so
that the binaries can be published and resolved in some basic form. There are various missing pieces. For example, dependency meta-data is not
published, and resolved binaries have the incorrect names on some platforms.

## Some terminology

### Producer project

The project that compile, links and publishes a binary to be shared. This sharing may happen within the same build or across several different builds.

### Consumer project

A project that uses the published binary in some form. In the case of a published library, the consumer usually installs, links against the library,
and runs the result. In the case of a published executable, the consumer generally installs and runs the executable.

<a name="shared-libraries"></a>
# Publishing and resolving dynamic libraries

## Use cases

A producer project produces a single dynamic library, for a single platform. The library depends on zero or more other dynamic libraries.
The library is published to a repository.

A consumer project resolves the library from the repository, and links an executable against it. Some libraries may be installed on the consumer
machine, and the remaining libraries are available in a repository.

Out of scope is the publishing of the resulting executable.

## Implementation

This is partially implemented. To finish this:

* Producer project publishes the dependency meta-data for the library.
* Producer project published the library's export file (.lib) when building on Windows.
* Consumer project uses correct names for resolved libraries. On linux, for example, the file name must match the so_name linked into the binary.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Separate out compile, link and runtime dependencies. On Windows, for example, the .lib file is required at link time and the .dll file is required at runtime.
* Consumer project installs the libraries into a location where the executable can find them, with their correct names.
* Consumer determine which libraries are already installed on the machine, and uses these from their installed location at link time.

<a name="executables"></a>
# Publishing and resolving executables

## Use cases

A producer project produces a single executable, for a single platform. The executable depends on zero or more dynamic libraries.
The executable is published to a repository.

A consumer project resolves the executable from the repository, and executes it. Some libraries may be installed on the consumer machine, and the
remaining libraries are available in a repository.

## Implementation

This is partially implemented. To finish this:

* There are a number of tasks in common with [publishing and resolving dynamic libraries](#shared-libraries) above.
* Producer project publishes the dependency meta-data for the executable.
* Consumer project uses correct names for resolved executables.
* Consumer project sets the UNIX file executable permission for resolved executables on UNIX filesystems.
* Consumer project installs the executable and libraries into a location where they are executable.

<a name="multiple-architectures"></a>
# Binaries built for multiple Intel x86 architectures

## Use cases

A producer project compiles and links a dynamic library for multiple Intel x86 architectures, for a single operating system. The library has zero or more dependencies.
The library is published to a repository.

A consumer project links and runs an executable against the appropriate variant of the library, resolved from the repository.

Out of scope for this work is support for other chipsets, or projects the build a library for multiple chipsets.

## Implementation

To implement this:

* Add appropriate tasks so that producer project can compile, link and publish the binaries for all architectures in a single build invocation.
* Add some mechanism for the developer to select the architectures they are interested in from the command-line and tooling API.
* Include in the published meta-data information about which cpu + pointer size (32bit vs 64bit) each binary was built for.
* Consumer project selects the binaries with the appropriate cpu + pointer size when resolving the link and runtime dependencies for the executable.
* Allow compiler and linker settings to be specified for each architecture.
* Allow resolved binaries to have the same file name across architectures. For example, a dynamic library should be called libsomething.so regardless
  of whether it happens to be built for the x86 or amd64 architectures.

To generate the binaries:

* For windows, run the 'x86' or 'x86_amd64' compiler.
* For linux + intel CPU, run with gcc with -m32 or -m64.
* For mac, run gcc with -arch i386, -arch x86_64, -arch ppc, -arch ppc64. Can include multiple times to generate a universal binary.

<a name="multiple-operating-systems"></a>
# Binaries built for multiple operating systems

## Use cases

A producer project compiles, links and publishes a dynamic library for multiple combinations of operating system and architecture. The library depends on zero or
more dynamic libraries. The library is published to a repository.

A consumer project compiles, links and runs an executable against the libary.

Out of scope is cross compilation for other platforms.

## Implementation

To implement this:

* Add appropriate tasks so that the producer project can compile, link and publish the binaries for the current machine's chipset.
* Allow a given version of the library to be built and published from multiple machines. That is, the binaries for the library are published
  incrementally over a period of time.
* Include in the published meta-data information about which operating system + version each binary was built for.
* Consumer project selects the binaries for the appropriate operating system when resolving link and runtime dependencies for the executable.
* Allow compiler and linker settings to be specified for each operating system.
* Allow dependencies to be declared for each operating system.
* Allow resolved binaries to have the same file name across operating system. For example, a dynamic library should be called libsomething.so on both linux and solaris.

<a name="header-only-libraries"></a>
# Header-only libraries

## Use case

Producer project publishes a library consisting of header files only (e.g. a library of C++ template classes).

Consumer project compiles an executable against this library.

## Implementation

To implement this:

* Allow zero or more binaries for a given library at link and runtime. Also allow for zero or more header files.

<a name="debug-and-release-binaries"></a>
# Debug and release binaries

## Use case

Producer project publishes a dynamic library with both debug and release variants.

Consumer project compiles, links and debugs an executable against this library.

## Implementation

To implement this:

* Producer project publishes the program database file (.pdb) for Windows binaries.
* Producer project publishes the source files for the library.
* Include in the published meta-data, information about whether the binary is a release or debug binary.
* Separate out debug time dependencies from runtime. On Windows, the pdb files are required to debug. In addition, the source files are required
  on all platforms.
* Consumer project selects the approprate release or debug library binraries when resolving link, execute and debug dependencies.
* Consumer project installs the pdb and source files into the appropriate locations, so the debugger can find them.
* Allow compiler and linker settings to be specified separately for release and debug binaries.

To generate the release binaries (default):
* Compile with -DNDEBUG
* Compile with -g0

To generate the debug binaries (default):
* Disable optimisation.
* Compile with -g or /Zi
* Link with /DEBUG

<a name="static-libraries"></a>
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

<a name="tests"></a>
# Running tests

TBD

<a name="multiple-binaries"></a>
# Publishing multiple binaries from a project

TBD

# Open issues

* General purpose tree artifact.
* Handling for system libraries.
* Building for multiple chipsets.
* Selecting a compatible architecture at resolve time. For example, if I'm building for the amd64 cpu, I can use an x86 cpu + 64bit pointer architecture.
* Selecting a compatible operating system version at resolve time.
* Selecting a compatible release binary when the debug binaries are not available.
* Need to include meta-data about which runtime a DLL is linked against. Same for dynamic libraries, but less common.
* Need to include meta-data about which optimisation level a binary is compiled for.
* Cross compilation.
* Custom variants.
