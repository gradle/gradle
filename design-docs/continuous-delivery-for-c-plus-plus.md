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

A library binary that is linked into an executable or shared library at runtime.

### Static library binary

A library binary that is linked into an executable or shared library at link time.

### Native component

A software component that runs on the native C runtime. This refers to the logical entity, rather than the physical.

A given component usually has one or more binaries associated with it, for various different operating systems, architectures and so on.

### Native application

A native component that represents an application.

### Native library

A native component that represents a library to be used in other native components.

# Completed stories

See [continuous-delivery-for-c-plus-plus.md](done/continuous-delivery-for-c-plus-plus.md) for completed stories.

# Milestone 1

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
- Rework the plugins so that there is something like:
    - A `cpp-lang` plugin, which adds support for C++ source sets and compiling them to object files.
    - A `c-lang` plugin, which adds support for C source sets and compiling them to object files
    - An `assembler-lang` plugin, which adds support for assembler source sets and compiling them to object files.
    - A `native-binaries` plugin, which adds the base support for native binaries and components.
    - 'cpp', 'c' and 'assembler' plugins, which apply the language plugin + native binary support
- Change `compilerArgs`, `define` and `macros` methods on NativeBinary so that they are language-specific
    - Replace existing methods with `cppCompiler.args`, `cppCompiler.define` and `cppCompiler.macros`.
    - Introduce `cCompiler` equivalents
- Change the GCC toolchain to use `gcc` to link when there is no C++ source included in a binary.

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

# Milestone 2

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

- Need to handle `#import` with Visual C++, which may reference a `.tld` file.
- Should not parse headers that we deem to be unchanging: 'system' libraries, unchanging repository libraries, etc.

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

### Open Issues

- Similar functionality for Visual C++
- Use only 'g++' and 'gcc' frontends for GCC (see http://stackoverflow.com/questions/172587/what-is-the-difference-between-g-and-gcc)

## Story: Support Clang compiler

### Test cases

- Build all types of binaries from all supported languages using Clang.
- Change test apps to detect which compiler is used to compile and assert that the correct compiler is being used.

### Open issues

- Fix when running under cygwin

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
9. Add a sample

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

### Open issues

- Source set with srcdirs + patterns probably does not work for resource files
- For a static library, the .res file forms part of the library outputs
- Generate and link a version resource by convention into every binary?
- Include headers?

# Milestone 3

## Story: Create functional Visual Studio solution for single-project build with multiple components

- For each available component choose a single binary variant that is the 'development binary'. Later stories will allow these to be specified.
- Add a task to create a Visual Studio solution file for each 'development binary'.
- For each binary in the dependency graph of the 'development binary', create a Visual Studio project file.
- When mapping binary to Visual Studio project:
    - map `buildType` to `configuration`
    - map `targetPlatform.architecture` to `platform`
    - map linkage and `flavor` to separate project files
- Wire the default configuration for the solution to the correct configurations in the various visual studio projects.

### Test Cases

- Single executable gets mapped to Visual Studio solution + project. Generating solution for executable creates project file
  with configuration for 'development binary' which is wired into solution file.
- Generate solution for executable that depends on the shared variant of a library.
    - Project file for shared library.
    - Project file for executable includes references to library projects.
    - Solution file references both projects and links to correct configurations for 'development binary'
- Generate solution for executable that depends on the static variant of a library.
- Generate solution for executable that depends on a shared library that in turn depends on another shared library.
- Generate solutions for 2 executables that depend on same shared library
- Generate solutions for 2 executables that depend on different linkages of same library.
- Generate solution for component with no sources
- Generate solution for component with mixed sources
- Generate solution for component with windows resource files
- Solution files for 2 executables that reference different build types of the same shared library
- Diamond dependency - :a:exe -> :b:lib1 -> :x:lib
                              -> :x:lib
- Transitive dependency on both shared and static linkage - :a:exe -> :b:lib1 -> :x:lib.static
                                                                   -> :x:lib.shared

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

## Story: Handle project cycles in component dependency graph

- Add `Map` as an alternative notation for `DependentSourceSet.lib` and `NativeBinary.lib`. All attributes are strings.
    - Required attributes are: `library` (the library name)
    - Optional attributes are: `project`, `linkage` (static, shared)
- Create a lazy NativeDependencySet that can resolve the map of attributes, looking up the project if required.

### User visible changes

    sources.main.cpp.lib library: 'sameProjectLib'
    sources.main.cpp.lib project: ':projectLib', library: 'mylib'
    sources.main.cpp.lib project: ':projectLib', library: 'myStaticLib', linkage: 'static'

### Test cases

- Dependency on library in same project
- Dependency on static library in a different project
- Dependency on shared library in different project
- Transitive dependency graph A:mainExe -> B:sharedLibrary -> C:sharedLibrary
- Cycle between projects: A:mainExecutable -> B:library -> A:library
- Dependency on library in a different project using configuration-on-demand
- Failure cases where: project does not exist, library does not exist, invalid linkage

## Story: Create functional Visual Studio solution for multi-project build with multiple components

- Change `VisualStudioProjectRegistry` so that it is responsible for locating a VisualStudioProjectConfiguration base on the NativeDependencySet,
  rather that the resolved LibraryBinary.
    - For project dependencies, delegate to the `VisualStudioProjectRegistry` in the depended-on project.
    - Change `VisualStudioProject.projectFile` to return the path to the generated project file within the owning project
    - When adding a reference to a dependent project to a `VisualStudioProject`, supply a key that can be used to resolve the project and the mapped component+flavor+linkage.
- `VisualStudioProject` and `VisualStudioSolution` will need to deal with files rather than file names to be able to reference vs project files from other projects.
- When creating a Visual Studio project for a native binary, create configurations for all buildable variants that differ only in `buildType` and `targetPlatform`.
    - This prevents any problems where a partial graph will result in breaking existing solutions that use the same projects:
      the same visual studio project is always produced given a particular native binary.
    - If multiple platform variants would not be differentiated based on architecture (Visual Studio 'platform),
      then include platform name in configuration name.
- Visual studio project files will live with Gradle project that owns the relevant component.

### Test Cases

- Transitive project dependencies - :a:exe -> :b:lib1 -> :c:lib2
- Mixed multi-project with multiple components per project
- Multi-project where :a:exe -> :b:lib1 -> :a:lib2 (Gradle project cycle)
- Multiple component that all use defaults for platform, build types and flavours

### Open issues

- Handle generated source.
- Handle case where target operating system for some variants is not windows.
- Handle case where build tools for a variant are not available for visual studio (eg exclude it with a warning or disable it - if possible).
- Handle case where component depends on a component in a project without the visual-studio plugin applied (eg auto-apply it or give reasonable error message).
- Command-line interface is kind of awkward when there are multiple components with the same name in the project hierarchy.
- Sync extensions in the filters file with those on the corresponding source sets.
- Generate a project files for VS 2010, VS 2012 or VS 2013 as appropriate

## Story: Customise generated Visual Studio files

### Use case

Developer wishes to use source control integration from within Visual Studio, and must extend generated config files with
project-specific configuration. Solution file needs to contain additional per-project configuration.

### Implementation

- Expose `visualStudio` extension with `solutions` container of `VisualStudioSolution` and `projects` container of `VisualStudioProject`
- `VisualStudioSolution.projects` provides the set of projects referenced by the solution.
- Add `VisualStudioSolution.solutionFile.withText(Action<? super String>)` to modify the solution file content.
- Add `VisualStudioProject.projectFile.withXml(Action<? super XmlProvider>)` and
  `VisualStudioProject.filtersFile.withXml(Action<? super XmlProvider>)` to modify these files
- Add visual studio sample that applies source control configuration to Visual Studio project

### DSL

    visualStudio {
        solutions.all { solution ->
            solutionFile.withText {
                solution.projects.each {

                }
            }
        }
        projects.all { project ->
            projectFile.withXml {
                ...
            }
            filtersFile.withXml {
                ...
            }
        }

### Test cases

- Sample integration test
- Add project-specific configuration to multiple project files
- Add solution-specific configuration to solution file
- Add configuration per project to solution file

## Story: Allow a library to depend on the headers of a component

### Use cases

1. Producer project publishes a library consisting of header files only (e.g. a library of C++ template classes, utility library).
Consumer project compiles an executable against this library.

1. Producer project defines api library together with implementation libraries. Consumer project creates multiple executables
from the same sources that link against different implementation libraries.

### User visible changes

    executables {
        main {}
    }
    libraries {
        templateLibrary {}
        apiLibrary {}
        implLibrary {}
    }
    sources.main.cpp.lib library: 'templateLibrary'
    sources.main.cpp.lib library: 'apiLibrary', linkage: 'api'
    sources.main.cpp.lib library: 'implLibrary'

### Implementation

- Include 'api' as a new linkage for a Library. Add `ApiLibraryBinary` and create instances for each Library.
- `Library.api` return a `NativeLibraryRequirement` with linkage = 'api'
- `ApiLibraryBinary.resolve()` will return a NativeDependencySet that contains only the public headers of the library.
- When mapping to visual studio:
    - Ensure that the include path of the referencing project includes the header path from the api binary
    - Do not generate a visual studio project for the api binary
- Close source sets and a) don't create tasks if empty, b) all linkages have no link-time/runtime files.
- Don’t create tasks for empty source sets
- For empty library, all linkages have no files for linking or installing

### Test cases

- Separate api and implementation libraries: executable compiles against api and links with impl.
- Utility library defines functions in header files. Executable is built using those functions.
- Utility library provides a different set of header files for debug and release variants.
- LibraryA provides an api and an implementation. LibraryB provides an alternate implementation.
  Build executable linking against LibraryA api and LibraryB implementation.
- Use api linkage to work-around library dependency cycle
- Compilation succeeds and linking fails when executable requires library, but only declares dependency on api.
- Visual studio solution for executable with separate api library and implementation library.

### Open issues

- Better mapping to Visual Studio

## Story: Component depends on a pre-built library

### User visible changes

    model {
        repositories {
            prebuilt {
                boost_regex {
                    headers {
                        srcDir '../../libs/boost_1_55_0/boost'
                    }
                    targetPlatforms "x86", "x64"
                    binaries.all { binary ->
                        // Locate the exact boost binary required
                        if (binary.toolChain.visualCpp) {
                            outputFile = file("../../libs/boost_1_55_0/lib/libboost_regex-vc71-mt-d-1_34.lib")
                        } else {
                            outputFile = file("../../libs/boost_1_55_0/lib/libboost_regex-gcc71-mt-d-1_34.a")
                        }
                    }
                }
            }
        }
    }
    sources.main.cpp.lib library: 'boost_regex'

### Implementation

- Pull methods of `NativeComponent` and `NativeBinary` that only apply to components/binaries that are _built_ by Gradle
  into a separate interface.
- No 'outputFile' on a binary: static[archiveFile, debugFile], shared[libraryStub, sharedLibrary, debugFile]
- Add a new `NativeComponent` subtype that represents a `PrebuiltLibrary`, and respective binary subtype.
- Add a new `prebuiltLibraries` container for `PrebuiltLibrary` instances (need a better name for this)
- Do not create tasks for binaries that are not built by Gradle
- Output files should be specified in `binaries.all {}` block
- Headers of prebuilt libraries are available when compiling for all linkages
- Link-time and run-time files are determined as per 'regular' libraries
- Prebuilt binaries are not included in Visual Studio (except as included headers in dependent components)
- Install task does not copy pre-built libraries into the install image, and sets the appropriate path variables in the generated script.

### Test cases

- Use Gradle to build the library-portion of the HelloWorldApp, and treat it as a pre-built library linked into an executable.
    - Executable uses static variant of pre-built library
    - Executable uses shared variant of pre-built library. Ensure that when installed, the pre-built library is referenced from it source location.
    - Ensure no tasks are executed for pre-built library.
- Define multiple variants (buildType/platform) of a prebuilt library. Build multiple variants (buildType/platform) of executable using this library
- Define a header-only prebuilt library. Executable depends on 'api' linkage and references header files.
- Generate visual studio solution for executable that depends on prebuilt library.
- Useful error message when:
    - no output file for selected binary
    - output file specified does not exist
    - library not found in project or prebuilt libraries
    - library with name in prebuilt libraries, but other project specified

### Open issues

- Allow these to be set as toolchain specific linker args (ie -l and -L) as well.
- Sources of prebuilt libs
- Dependencies of prebuilt libs
- Libraries that are buildable but not built by us
- System libraries
- Convert 'dependency' syntax to add to this container?
- Fix paths in linker: don't link with absolute path
- Make it easier to define a pattern for actual file locations

## Story: Allow source sets to be generated

- `LanguageSourceSet` extends `BuildableModelElement`
    - `generatedBy()` method specifies task(s) that generate the source. Source files are inferred from the task outputs.
- Move `tasks` property up from `NativeBinary` to `BuildableModelElement`. `NativeBinary` may override this to specialise the property type.
- Wire task outputs to source/exportedHeaders inputs for each `LanguageSourceSet` in a component.


    sources {
        main {
            generatedC(CSourceSet) {
                // Source files are inferred from the outputs of the task
                generatedBy tasks.someTask
            }
        }
    }

## Story: Support CUnit test execution

### Implementation

Generally, C++ test frameworks compile and link a test launcher executable, which is then run to execute the tests.

To implement this:
* Define a test source set and associated tasks to compile, link and run the test executable.
* It should be possible to run the tests for all architectures supported by the current machine.
* Generate the test launcher source and compile it into the executable.

### Open issues

* Need a `unitTest` lifecycle task, plus a test execution task for each variant of the unit tests.
* Need to exclude the `main` method from unit test sources.
* Generate a test launcher that is integrated with Gradle's test eventing.
* Automatically detect and register tests in test source files; don't require them to be explicitly registered. (Similar to JUnit and TestNG tests).
* Generate nice HTML reports for CUnit test output

# Bugfixes

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

# Milestone 4

## Story: CI coverage for more tool chains

- Visual Studio 2013
- GCC 3
- XCode on OS X

## Feature: Flexible source sets

A sequence of stories to make source sets much more flexible, and to improve the conventions of the source sets for a component:

### Story: Language source sets filter source files by file extension

This story introduces applies some default extensions to each language source set.

- For each language (C, C++, Objective-C, Objective-C++, Assembly, Windows resources), apply the appropriate file extensions using `sourceSet.source.filters`.

#### Test cases

- A native component with the source and headers for each language all in the same source directory.
- Can include or exclude source files in a source set by file extension.
- Fix sample and int tests that apply patterns to select source files with a given extension so that they no longer do.

### Story: Introduce implementation headers for native components

This story introduces a set of headers that are visible to all the source files of a component, but are not visible outside the component.

1. Introduce a `HeaderSet` extends `LanguageSourceSet`. This represents a collection of native headers.
    - Introduce a base native language plugin.
    - Base native language plugin adds a `HeaderSet` called `sharedHeaders` for each native component.
    - Defaults to include source dir `src/${component.name}/include`
1. Allow `DependentSourceSet` instances to declare dependencies on `HeaderSet` instances.
    - These should resolve to a `NativeDependencySet` instance that contains the source directories in the header set at compile time, and nothing at link or runtime.
    - Base native language plugin add a dependency from each compiled language source set to the `sharedHeaders` header set.
    - This step means that any header added to `src/${component.name}/include` is visible to the compilation of every source file in the component.
    - This step also allows language-private headers to be defined.

#### Example

    libraries {
        mylib
    }

    sources {
        mylib {
            // Adjust the defaults for shared headers
            sharedHeaders {
                srcDirs = 'src/headers'
            }

            // Add a language-private header set
            cPrivateHeaders(HeaderSet) {
                srcDirs = 'src/c'
                include '**/*.h'
            }
            c {
                lib cPrivateHeaders
            }
        }
    }

##### Test cases

- Native library `mylib` defines some header files in `src/mylib/include` and `src/mylib/headers`.
    - Header files in both locations are visible at compile time for a C, C++, windows resource, Objective-C and Objective-C++ source file.
- Native library `mylib` defines some implementation headers in `src/mylib/include` and some public headers in `src/mylib/headers`
    - Header files in both locations are visible at compile time for a C source file in the component.
    - Headers in `src/mylib/include` are not visible to a C source file in another component that depends on `mylib`.
    - Headers in `src/mylib/headers` are visible to a C source file in another component that depends on `mylib`.
- Native library `mylib` does not define any header files.
    - Can compile and link `mylib` and use it in an executable. Need to use C as the implementation language.
- Native library `mylib` defines a set of headers that are visible to the component's C source, but not visible to:
    - C++ source in the same component.
    - C and C++ source in another component that depends on `mylib`.

#### Open issues

- Default location for the implementation headers
- Rename `lib()` to `dependsOn()` or similar?

### Story: Introduce public headers for native libraries

This story introduces a set of headers that are visible to all source files in a component and to all components that depend on the component.

1. Allow `HeaderSet` instances to declare dependencies on `HeaderSet` instances.
    - Change `HeaderSet` to extend `DependentSourceSet`.
    - These should resolve as above.
    - The transitive dependencies of a header set should be visible at compile time.
1. Introduce a public header set for native libraries
    - Base native language plugin adds a header set called `publicHeaders` for each native library component.
    - Defaults to include source dir `src/${component.name}/public`
    - Base native language plugin add a dependency from the `sharedHeaders` header set to the `publicHeaders` header set.
1. Make the public headers of a library visible to other components
    - When resolving a dependency on a native library component, only the headers in the `publicHeaders` set should be visible.
    - Remove `HeaderExportingSourceSet`
    - This step means that only those headers added to `src/${component.name}/public` are visible to consumers and implementation of a library.

#### Example DSL

    libraries {
        mylib
    }

    sources {
        mylib {
            // Adjust the defaults
            publicHeaders {
                srcDir 'src/headers/api'
            }
        }
    }

#### Test cases

- libraries.c depends on libraries.b depends on libraries.a
    - The public headers of libraries.a and libraries.b should be visible when compiling libraries.c
    - The implementation headers of libraries.a and libraries.b should not be visible when compiling libraries.c

#### Open issues

- Default location for public headers.
- Language specific public headers. Eg include these headers when compiling C in a consuming component, and these additional headers when compiling C++.
- Update the generated Visual Studio project so that different header sets are grouped within distinct "filters".

### Story: Configure the source sets of a component in the component definition

This story moves definition and configuration of the source sets for a component to live with the other component configuration.

1. Merge `ProjectSourceSet` and `FunctionalSourceSet` into a more general `CompositeSourceSet`.
    - This is simply a source set that contains other source sets.
    - This step allows arbitrary source sets to be added to the `sources { ... }` container.
1. Allow a component's source sets to be defined as part of the component definition:
    - Replace `NativeComponent.getSource()` with a `getSources()` method return a `CompositeSourceSet`. This should be the same instance that is added to the `project.sources { ... }` container.
    - Add a `NativeComponent.source(Action<? super CompositeSourceSet>)` method.
    - Change language plugins to add source sets via the component's source container rather than the project's source container.
    - This step allows configuration via `component.source { ... }`.
1. Review samples to make use of this.

#### Example DSL

    libraries {
        mylib {
            sources {
                publicHeaders {
                    srcDir = 'src/headers/api'
                }
                sharedHeaders {
                    srcDir = 'src/headers/shared'
                }
                c {
                    lib libraries.otherlib
                }
                cpp {
                    include '**/*.CC'
                }
            }
        }
    }

    // Can also reach source sets via project.sources
    sources {
        mylib { ... }
    }

#### Open issues

- Flatten out all source sets into `project.sources`. Would need to use something other than a named domain object container.

## Feature: Objective-C support

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

#### Open issues

- Cross-compilation for iPhone.
- Make toolchain extensible so that not every toolchain implementation necessarily provides every tool, and may provide additional tools beyond the
  built-in tools.
- Fix `TargetPlatformConfiguration` and `PlatformToolChain` to make them extensible, so that not every configuration supports every tool.
- Gcc and Clang tool chains need to provide the correct compile and link time arguments on OS X and Linux.

### Story: Incremental compilation for Objective-C and Objective-C++

- Change the Objective-C and Objective-C++ task implementations to apply incremental compilation, similar to the C and C++ tasks.
- Source import parsing should understand `#import` directive.

#### Test cases

- Add an `AbstractLanguageIncrementalCompileIntegrationTest` subclass for each of Objective-C and Objective-C++
- Source file uses `#include` to include a header file.
- Source file uses `#import` to include a header file.

# Later Milestones

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

### Open issues

* When no Platform architecture/os is defined, assume the current platform architecture/os, not the tool chain default.
    * This will require detecting the current platform, and supplying the correct tool chain arguments when building.

## Story: Improved DSL for tool chain configuration

This story improves the DSL for tweaking arguments for a command-line tool that is part of a tool chain, and extends this
ability to all command-line based tool chains. It also permits the configuration of tool chain executables on a per-platform basis.

### Implementation

* Add `CommandLineToolInvocation` extends `org.gradle.nativebinaries.Tool` with read-write String property `executable`.
* Rename `GccTool` to `CommandLineTool` and change to have `withInvocation(Action<CommandLineToolInvocation>)` in place of `withArguments`
* Remove tool-specific getters from `Gcc`, and instead make `Gcc` serve as a NamedDomainObjectSet of `CommandLineTool` instances.
    * Continue to register a `CommandLineTool` for every supported language.
* Allow the `eachInvocation` method to override the default executable to use.
* Extract `CommandLineToolChain` interface out of `Gcc` and introduce similar functionality to VisualCpp and Clang tool chains.
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

## Story: Only use gcc/g++ front-ends for GCC tool chain

* Use 'gcc -x assembler -c' for assembly sources
* Use 'gcc' to link instead of 'g++': add -lstdc++ to linker args for C++ sources. Similar for objective-C++.
* Remove 'as' from the tools in the GCC tool chain.

## Story: Improve definition of Platform, Architecture and Operating System

In order to make it easier for users to publish and resolve native binary dependencies, this story introduces a set of conventional
Platforms, as well as improving the way that Platform components (Architecture, OperatingSystem) are defined. While it will still be possible
to define a custom Platform and build and publish binaries for that platform, the conventions introduced here should cover most common
platform variants that would be published and shared publicly.

This story also aggregates a bunch of review items that relate to Architecture and Operating System.

### User visible changes

    model {
        operatingSystems.add customOs
        architectures.add customArch
        platforms {
            // Custom platforms
            custom {
                operatingSystem operatingSystems.customOs
                architecture architectures.customArch
            }
            customCurrent {
                operatingSystem operatingSystems.current()
                architecture architectures.current()
            }
        }
    }

### Implementation

- Wrap `OperatingSystemNotationParser` in an `OperatingSystemRegistry` as a factory for `OperatingSystem` instances.
    - Instances are retrieved by name or alias. A future story will allow architectures to be obtained by selection criteria.
- Wrap `ArchitectureNotationParser` in an `ArchitectureRegistry`.
- Add `OperatingSystemRegistry.getCurrent()` and `ArchitectureRegistry.getCurrent()`, that locates the instance the represents the current
  architecture / os.
    - Should use native-platform's SystemInfo to probe the kernel architecture and fall back to 'os.arch' system property if
      native-platform is not available.
    - Should use 'os.name' system property to determine current os.
    - The returned values should use a canonical name, and treat the os-specific names as an alias.
- Register conventional elements in `PlatformContainer` : come up with some good canonical names
    - windows + x86 / x86_64 / ARM
    - linux + x86 / x86_64 / ???
    - OSX + x86_64
    - Solaris + Sparc V8 / Sparc V9 / x86 / x86_64
- Change PlatformContainer so that instances are immutable once created
- Add PlatformContainer.current() to get the platform with the current os and architecture.
- Use `ArchitectureRegistry` to obtain instances used in `DumpbinBinaryInfo`
- Only build component for the current platform unless it is targeted for multiple platforms.

### Test cases

- Verify arch/os of binary built for current (default) platform in BinaryPlatformIntegrationTest

### Open issues

- Way to construct and register new OperatingSystems / Architectures
- Canonical names for conventional architectures
- Canonical names for conventional platforms
- How to make Platform instance immutable
- Consistent API for Architecture and OperatingSystem: either public method on both [os.isWindows(),arch.isAmd64()] or only on internal api.
- Include ABI in architecture so that the correct prebuilt library can be selected for a tool chain
- When no Platform architecture/os is defined, assume the current platform architecture/os, not the tool chain default.
    - This will require detecting the current platform, and supplying the correct tool chain arguments when building.
    - We can then remove the concept of the "tool chain default" platform, and always explicitly tell the tool chain which platform to build for.

## Story: Include all macro definitions in Visual Studio project configuration

Include both `cppCompiler.define` and `cppCompiler.args '/D...'`.

- Allow macro definition to be set as tool specific args (e.g. -D or /D) and queried via the `macros` property.
- Support this for all tools with a preprocessor:
    - c++ compiler
    - c compiler
    - resource compiler
    - assembler (if we've changed the implementation to use the preprocessor)

## Story: Include all include paths in Visual Studio project configuration

Include libraries define as source set dependencies, binary dependencies and values supplied via `cppCompiler.args '/I...'`.

- Add a 'includePath' property for all tools with a preprocessor (as for the previous story)
- Allow these to be set as tool specific args (e.g. -I or /I) and queried via the `includePath` property.

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

### Test cases

- Install and run an executable that:
    - Use a (static, shared) library that depends on another (static, shared) library.
    - In each case, verify that only shared libraries are included in the install image.
    - In each case, remove the original binaries before running the install image.

### Open issues

- Need to apply conflict resolution as we can't include the static and shared binaries for a given library at link time.
- Need to be able to deal with the fact that only position-independent binaries can be linked into position-independent binaries
    - Make it possible to build a position-independent variant of a static library binary, for those toolchains that don't generate position-independent
      binaries by default.
    - Add the '-fPIC' flag when compiling position-independent variant of a static library
    - Change dependency resolution to choose the position-independent variant of a static library when linking into a shared library, or any other
      position independent binary.
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

## Feature: Variant aware dependency resolution

Depends on a number of stories in [dependency-resolution.md](dependency-resolution.md). The plan is roughly:

- Dependency resolution is based on resolving a graph of components.
- Model native components as a `SoftwareComponent`.
- Model dependencies on native components as a dependency declaration.
- Introduce a way to create a set of dependency declarations, and a way to resolve a set to a graph of components.
- Allow a the native language plugins to provide the rules for selecting the dependencies and artifacts for a native component.
- Replace `DependentSourceSet.libs` and `dependency` with a set of dependency declarations.
- Replace `LibraryResolver` and `NativeDependencySet` with the above.

## Story: Allow easy creation of all variants, all possible variants and a single variant for each component

- Add an `assemble` lifecycle task for each component.
- Running `gradle assemble` should build all binaries for the main executable or library
- Add a task for creating a single 'developer image' for each component.
    - Build `debug` variant where possible
    - Build shared library variants
    - Build for the current Operating System and architecture
- Review samples and remove convenience tasks that are no longer required

## Story: Allow a binary to be defined that is not part of a component

- Allow native binary instances to be added manually.
- Change `NativeBinary` so that not every binary has an associated component.

### Test cases

- Can define a standalone executable, shared library or static library binary, and the appropriate tasks are created and configured appropriately.
- Can define and configure standalone compile and link tasks.

## Story: Automatically include debug symbols in 'debug' build types

- Add conventional build types: `debug` and `release`
- Add a `debug` property to `BuildType`. The conventional 'debug' build type should have `debug == true`.
- Gcc should compile sources for `debug` build types with the `-g` flag
- Visual C++ should
    - Compile sources for `debug` build types with `/Zi /DDEBUG` and non-debug build types with `/DNDEBUG`.
    - Link binaries for `debug` build types with `/DEBUG` and non-debug build types with `/RELEASE`.
- When resolving the dependencies of a binary `b`, for a dependency on library `l`:
    - Prefer a binary for library `l` that has a build type with a matching name.
    - Otherwise, select a library `l` that has a build type with a matching `debug` flag.

### Open issues

- Need some way to probe for debug information in binaries
- Tool chain plugins add extension to `BuildType` to add in specific configuration.
- GCC debug
    - Convenience to switch on or off with defaults
    - Debug information format, GDB extensions on or off
        - Has impacts on dependency resolution, as some formats are stored externally
    - Debug level (0 - 3)
- GCC profile
    - Convenience to switch on or off with defaults
    - gprof extensions on or off
- GCC optimise
    - Needs to compile all source files in one invocation to do the best job
    - Optimise level (0 - 3)
    - Size vs speed
- Visual C++ optimise
    - size vs speed
    - prefer size vs speed
    - Max optimise
- Visual C++ debug
    - On or off

## Story: Generate source from Microsoft IDL files

- Add a `microsoft-idl` plugin
- For each `FunctionalSourceSet` add an IDL source set.
- Add the IDL source set as input to the C source set.
- Add some way to declare what type of thing is being built: RPC client, RPC server, COM component, etc
- For each IDL source set adds a generate task for each target architecture
    - Not on Windows
    - Fail if toolchain is not Visual C++
    - Use `midl` to generate server, client and header source files.
        - Invoke with `/env win32` or `/env amd64` according to target architecture.
        - Invoke with `/nologo /out <output-dir>`
- Add `midl` macros and args to each `NativeBinary` with Windows as the target platform
- Add a sample

### Test cases

- Can build a client and server executable.
- Can build a COM DLL.
- Incremental
- Removes stale `.c` and `.h` files

### Open issues

- Source set with srcdirs + patterns probably does not work for IDL files.
- Include headers?
- Need to make `LanguageSourceSet` extend `BuildableModelElement`.

## Story: Use Windows linker def files

### Implementation

* A `.def` file lists `__stdcall` functions to export from a DLL. Can also use `__declspec(dllexport)` in source to export a function.
* Functions are imported from a DLL using `__declspec(dllimport)`.

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

Usually, it is possible to combine different instruction sets in the same binary. So, when linking a binary targeting the amd64 CPU, it is fine to link
against a library built for the x86 CPU. It is not possible to combine different data models in the same executable.

On OS X, a binary may target multiple architectures, as a universal binary. It should be noted that a universal binary simply supports more than one
architectures, but not necessarily every architecture as the name suggests. For example, a universal binary might include x86 & amd64 support but no
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

A consumer project compiles, links and runs an executable against the library.

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

Usually, it is fine to link a release build of a library into a debug binary, if the debug variant is not available. It is not fine to link a debug
build of a library into a release binary. On Windows, it is not ok to link a release build of a library into a debug binary.

On some platforms, additional artifacts are required to debug a binary. On gcc based platforms, the debug information is linked into the binary.
On Windows, the debug information is contained in a separate program database file (`.pdb`).

On all platforms, the source files for the binary are required.

To implement this:

* Producer project publishes the program database file (.pdb) for Windows binaries.
* Producer project publishes the source files for the library.
* Include in the published meta-data, information about whether the binary is a release or debug binary.
* Separate out debug time dependencies from runtime.
* Consumer project selects the appropriate release or debug library binaries when resolving link, execute and debug dependencies.
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

## DSL cleanups

- Improve customising the tasks for building a binary
- Improve doing stuff with the install task
- Some way to configure/use all native components (instead of `libraries.all { ... }` and `executables.all { ... }`)
- Have some way to replace the default source sets or configure one without redefining all
- Don't create variants that can never be built
- Model language dialect and add this to source set, e.g. GCC, visual C++, ANSI C. A tool chain can compile some set of dialects.
- Model operating system as a dependency declared by a source set.
    - Can use this to determine which source sets to include in a given binary.
- Model ABI as part of platform. A tool chain can produce binaries for some set of ABIs.
- Use the term 'Visual Studio' consistently instead of 'Visual C++'
- Test coverage for Visual studio 2012, 2013
- Tool chain represents an installation. Must be easy to configure the discovered tool chain, and configure extra installations.
- Improve name spacing in the DSL, so that only the current thing (or no thing) is reachable without some kind of qualification.

## Source

- Split headers source set out of HeaderExportingSourceSet subclasses.
- Introduce a composite source set that groups source into api + impl.
- Allow dependencies to be declared between source sets
    - native language source set A depends on header source set B makes the headers of B available when compiling A.
    - native language source set A depends on native language source B makes the object files of B available when linking A into a binary.
    - header source set A depends on header source set B makes the headers of B available when compiling something that depends on A.
- Replace `SourceDirectorySet` with something that is actually a set of source directories.
    - Use this for sources and headers
    - Can access as a set of directories, a set of files or a file tree
    - Allow individual files to be added
    - Allow custom file extensions to be specified
- Source sets are children of components
    - Only add source sets for supported languages to the children of native components
- Language source sets should filter files with a given set of extensions: C/C++/windows-res/assembler/Objective-C/Objective-C++/header
- A source set may have its own dependencies, visible only to the source set
- A native component has a shared headers source set with its own dependencies, visible to all source sets of the component
- A library component has an API, defaults to the shared headers
- When compiling, only the API of dependencies should be visible
- AssemblerSourceSet should implement DependentSourceSet (has source dependencies)
- Add source sets only to a component, have some way to describe how to include/exclude a source set
- Some conventional location for OS specific source for a component
- Allow encoding to specified on a source set, with default values as per java sources

## Compilation

- GRADLE-2943 - Fix escaping of compiler and linker args
- Understand and/or model -I/-L/-l/-D/-U
- GCC tool-chain uses gcc/g++ to link and gcc to assemble
- Source set declares a target language runtime, use this to decide which driver to use to link
- Don't fail if g++ is not installed when it is not required
- Support preprocessor macros for assembler
- Clean the environment prior to invoking the visual studio tools (eg clear `%INCLUDE%`, `%LIB%` etc)
- Don't create compile tasks for empty source sets
- Compile windows resource files with gcc/clang using [`windres`](http://sourceware.org/binutils/docs/binutils/windres.html)


## Target platforms

- Provide a better way to select the target platforms for a component
    - Use an alias or some kind of selector to define which target platforms to build for.
    - Selector can select multiple target platforms
- Define some conventions for platforms, along with some conventions for aliases/selectors and names
    - Remove the conventional `current` platform, instead infer the default target platform using the current operating system
      and a 'standard' architecture for that operating system on the current machine.
- Conventional architecture names
    - Intel 32bit: `ia-32`, `i386`, `x86`
    - Intel 64bit: `x86-64`, `x64` (Windows), `amd64` (BSD and Debian linux distributions, Solaris), `x86_64` (linux kernel, OS X, Fedora, GCC tools)
    - Intel Itanium: `ia-64`
    - PowerPC 32bit: `ppc`
    - PowerPC 64bit: `ppc64`
    - Sparc: ??
    - ARM: ??
- Conventional operating system names
- More platform test coverage:
    - ARM
    - 64 bit assembler
    - Cross compilation
- Not every Windows SDK that can build all the target architectures of a component. For example, the SDK distributed with Visual studio express can only build x86 binaries,
  so either don't use it if another, newer, version is available, or mark the other variants as unavailable.
- Not every GCC installation can build all the target architectures of a component. For example, can only build i386 binaries on an amd64 machine if the i386 multi-arch
  support is installed, so mark variants as unavailable when the target c runtime is not available.
- Support for itanium was dropped after Visual studio 2010, so if this architecture is requested, need to use a version of Visual Studio that can build this.

## Variants

- Add linkage as a variant dimension
- Need to handle universal binaries.
    - A component packaging that satisfies multiple points in the variant space.
    - Use `lipo` to merge two binaries into a single universal binary.
    - Transforms the meta-data for the component - same set of variants but different set of binaries.

## Toolchains

- DSL to declare that a toolchain supports certain target platform, and how to invoke the tools to do so.
- Introduce `XCode`, `Cygwin`, `MinGW` toolchains, to allow selection of specific gcc or clang implementations.
    - Use the `XCode` tool chain to determine mac-specific gcc args
    - Use the `Cygwin` and `MinGW` toolchains to provide additional path requirements for `InstallExecutable` task

    toolchains {
        gcc {
            platform(...) {
                ... configure platform implementation
                cppCompiler { compileSpec ->
                    ... compiler compiler invocation
                }
            }
        }
        visualStudio {
            ...
        }
        gcc3(Gcc) {
            installDir = 'someWhere'
        }
    }

- Prevent configuration of tool chains after availability has been determined.

## Structure

- Some common plugin that determines which tool-chains to apply
- Determine naming scheme for the 'native' domain
    - Fix package hierarchy
    - Consistent pattern with 'jvm' domain
    - Rename `subprojects/cpp`
    - Probably make language superior to runtime, and have tasks and plugins live under language root
- Enable package cycle checks


### Language plugins

- Add a 'development' install task, which chooses a single variant for an executable to install
- Need to make standard 'build', 'check' lifecycle tasks available too. The `assemble` task should build all buildable variants.
    - Reasonable behaviour when nothing is buildable on the current machine.
- Come up with consistent naming scheme for language plugins: 'cpp', 'c', 'assembler', 'java-lang', 'scala-lang', etc

### Performance

- Improve incremental build where the task needs to do some work to figure out the inputs.
- Add some performance tests
- Parallel compilation, some kind of consistency between parallel test execution, parallel task execution, etc.
- When compilation succeeds for some files and not for other files, then mark the files that were successfully compiled as successful and
  don't recompile them next time.

## Later

- Better way to see how the compiler is being invoked
- Make names less important

## Test coverage

- Update the UnknownOS CI build to run on java 7, and remove the "CAN_INSTALL_EXECUTABLE" test requirement
- Integration test coverage for 64-bit assembler with Visual C++
- Verify that the correct windows system libraries are available at link time
    - Use a test app that uses something like WriteFile() to write its hello world message to stdout. If it can link and run, then the paths are probably ok.

# Open issues

* Add ABI as an aspect of target platform.
* Output of any custom post link task should be treated as input to anything that depends on the binary.
* Route stdout to info level when linking a shared library using visual studio, to get rid of the pointless notice.
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
* Support for install distributions, which may bundle some, all, or none of the runtime dependencies (including language runtime libraries).
* Understand the various output file types: PE, ELF, Mach-O, COFF
* Bare-bones tool chain using GNU binutils
* Should be able to run the C preprocessor on assembler source file.
* Add support for cygwin-64 (uses a different data model to windows) and mingw under cygwin.
* Install task should generate a shell script as well as a batch script when running under cygwin.
* GCC and Clang under cygwin target the cygwin runtime, not the windows runtime.
* Add language level to C and C++ source sets.
* The `gcc` provided by XCode on OS X is actually a repackaged `clang`. Should distinguish between building with the `gcc` provided by XCode, and building with a real `gcc`
  install, eg via a ports toolkit.
* Adding other languages as external plugins.
* Consume dependencies from cocoapods repository.
* Publish to cocoapods repository.
* Consume dependencies from NuGet repository.
* Publish to NuGet repository.
* JNI plugin generates native header, and sets up the JNI stuff in $java.home as a platform library.
* Model minimum OS version.
    * For OS X can use -mmacosx-version-min option.
* Clean task for a binary

