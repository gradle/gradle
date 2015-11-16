This document describes a number of improvements to allow C++ projects to be built, tested, published are shared between teams.

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

# Milestone 3

## Story: Create functional Visual Studio solution for single-project build with multiple components (DONE)

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

## Story: Handle project cycles in component dependency graph (DONE)

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

## Story: Create functional Visual Studio solution for multi-project build with multiple components (DONE)

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

## Story: Customise generated Visual Studio files (DONE)

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

## Story: Allow a library to depend on the headers of a component (DONE)

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

## Story: Component depends on a pre-built library (DONE)

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

## Story: Allow source sets to be generated (DONE)

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

# Later milestones

## Story: Improved GCC platform targeting

When using GCC to build for a platform that is not the GCC default, different executable names should be used:

### Windows Host

Host windows + cygwin32/cygwin64 + mingw64:

- x86 windows target should use:
    - i686-w64-mingw32-gcc
    - i686-w64-mingw32-g++
    - i686-w64-mingw32-gcc-ar
    - i686-w64-mingw32-as
    - i686-w64-mingw32-ld
    - i686-w64-mingw32-windres
    - Locations of libs:
        - std c headers: /usr/include
        - std c lib: /usr/i686-w64-mingw32/<version>/
        - std c++ headers: /usr/i686-w64-mingw32/<version>/include/c++
        - std c++ lib: /usr/i686-w64-mingw32/<version>/
        - win sdk headers: /usr/i686-w64-mingw32/sysroot/mingw/include
        - win sdk lib: /usr/i686-w64-mingw32/sysroot/mingw/lib

- amd64 windows target should use:
    - x86_64-w64-mingw32-gcc
    - x86_64-w64-mingw32-g++
    - x86_64-w64-mingw32-gcc-ar
    - x86_64-w64-mingw32-as
    - x86_64-w64-mingw32-ld
    - x86_64-w64-mingw32-windres
    - Locations of libs:
        - Same scheme as above

Host windows + cygwin32/cygwin64 + mingw32:

- x86 windows target should use:
    - i686-pc-mingw32-gcc
    - i686-pc-mingw32-g++
    - i686-pc-mingw32-ar
    - i686-pc-mingw32-as
    - i686-pc-mingw32-ld
    - i686-pc-mingw32-windres
    - Locations of libs:
        - Same scheme as above

- amd64 targets not supported.

Host windows + cygwin32:

- x86 cygwin target should use:
    - i686-pc-cygwin-gcc
    - i686-pc-cygwin-g++
    - i686-pc-cygwin-gcc-ar
    - as
    - ld
    - windres

- amd64 cygwin target should use:
    - x86_64-pc-cygwin-gcc
    - x86_64-pc-cygwin-g++
    - x86_64-pc-cygwin-gcc-ar
    - x86_64-pc-cygwin-as
    - x86_64-pc-cygwin-ld
    - x86_64-pc-cygwin-windres

Host windows + cygwin64:

- x86 cygwin target should use:
    - i686-pc-cygwin-gcc
    - i686-pc-cygwin-g++
    - i686-pc-cygwin-gcc-ar
    - i686-pc-cygwin-as
    - i686-pc-cygwin-ld
    - i686-pc-cygwin-windres
    - Location of libs:
        - std c headers: /usr/include
        - std c libs: /lib/gcc/i686-pc-cygwin/<version>/
        - std c++ headers: /lib/gcc/i686-pc-cygwin/<version>/include/c++
        - std c++ libs: /lib/gcc/i686-pc-cygwin/<version>/

- amd64 cygwin target should use:
    - x86_64-pc-cygwin-gcc
    - x86_64-pc-cygwin-g++
    - x86_64-pc-cygwin-gcc-ar
    - as
    - ld
    - windres
    - Location of libs:
        - std c headers: /usr/include
        - std c libs: /lib/gcc/x86_64-pc-cygwin/<version>/
        - std c++ headers: /lib/gcc/x86_64-pc-cygwin/<version>/include/c++
        - std c++ libs: /lib/gcc/x86_64-pc-cygwin/<version>/

Host windows + mingw32:

- x86 windows target should use:
    - mingw32-gcc
    - mingw32-g++
    - mingw32-gcc-ar
    - mingw32-as
    - mingw32-ld
    - mingw32-windres

- amd64 targets not supported.

### Linux host

Host Ubuntu x86:

- x86 linux target should use:
    - i486-linux-gnu-gcc or i686-linux-gnu-gcc
    - i486-linux-gnu-g++ or i686-linux-gnu-g++
    - ar
    - as
    - ld

- amd64 linux target should use:
    ??

Host Ubuntu amd64:

- amd64 linux target should use:
    - x86_64-linux-gnu-gcc
    - x86_64-linux-gnu-g++
    - ar
    - as
    - ld
    - library locations:
        - std c headers: /usr/include
        - std c libs: /usr/lib/gcc/x86_64-linux-gnu/<version>
        - std c++ headers: /usr/include/c++/<version>
        - std c++ libs: /usr/lib/gcc/x86_64-linux-gnu/<version>

- x86 linux target should use:
    - as above, need to detect platform libs
    - library locations:
        - std c headers: /usr/include
        - std c libs: /usr/lib/gcc/x86_64-linux-gnu/<version>/32
        - std c++ headers: /usr/include/c++/<version>
        - std c++ libs: /usr/lib/gcc/x86_64-linux-gnu/<version>/32

Host OpenSUSE amd64:

- amd64 target:
    - library locations:
        - std c headers: /usr/include
        - std c libs /usr/lib64/gcc/x86_64-suse-linux/<version>
        - std c++ headers: /usr/include/c++/<version>
        - std c++ libs /usr/lib64/gcc/x86_64-suse-linux/<version>

Host Fedora amd64:

- amd64 target:
    - x86_64-redhat-linux-gcc
    - library locations:
        - std c headers: /usr/include
        - std c libs: /usr/lib/gcc/x86_64-redhat-linux/<version>
        - std c++ headers: /usr/include/c++/<version>
        - std c++ libs: /usr/lib/gcc/x86_64-redhat-linux/<version>

Host Ubuntu + mingw32:

- x86 windows target should use:
    - i586-mingw32msvc-gcc
    - i586-mingw32msvc-g++
    - i586-mingw32msvc-gcc-ar
    - i586-mingw32msvc-as
    - i586-mingw32msvc-ld
    - i586-mingw32msvc-windres
    - library locations:
        - std c headers: /usr/include
        - std c libs: /usr/lib/gcc/i586-mingw32msvc/<version>
        - std c++ headers: /usr/lib/gcc/i586-mingw32msvc/<version>/include/c++
        - std c++ libs: /usr/lib/gcc/i586-mingw32msvc/<version>
        - win sdk headers: /usr/i586-mingw32msvc/include
        - win sdk libs: /usr/i586-mingw32msvc/lib

Host Ubuntu + mingw64:

- x86 windows target should use:
    - i686-w64-mingw32-gcc
    - i686-w64-mingw32-g++
    - i686-w64-mingw32-as
    - i686-w64-mingw32-ar
    - i686-w64-mingw32-ld
    - i686-w64-mingw32-windres
    - library locations:
        - std c headers: /usr/include
        - std c libs: /usr/lib/gcc/i686-w64-mingw32/<version>
        - std c++ headers: /usr/include/c++/<version>
        - std c++ libs: /usr/lib/gcc/i686-w64-mingw32/<version>
        - win sdk headers: /usr/i686-w64-mingw32/include
        - win sdk libs: /usr/i686-w64-mingw32/lib

- amd64 windows target should use:
    - x86_64-w64-mingw32-gcc
    - x86_64-w64-mingw32-g++
    - x86_64-w64-mingw32-as
    - x86_64-w64-mingw32-ar
    - x86_64-w64-mingw32-ld
    - x86_64-w64-mingw32-windres
    - library locations:
        - Same scheme as above

Can use `-print-search-dirs` to give some details about the above. Only partially supported by clang.
Can use `-print-sysroot` to locate the directory containing system libraries. Not supported by clang and returns empty path in many environments.

### OS X host

Host OS X + Macports with mingw32:

- x86 windows target should use:
    - i386-mingw32-gcc
    - i386-mingw32-g++
    - i386-mingw32-as
    - i386-mingw32-ar
    - i386-mingw32-ld
    - i386-mingw32-windres

Host OS X + Macports with x86_64 elf:

- amd64 linux target should use:
    - x86_64-elf-gcc
    - x86_64-elf-g++
    - x86_64-elf-as
    - x86_64-elf-ar
    - x86_64-elf-ld

## Story: CI coverage for more tool chains

- Visual Studio 2013
- Visual Studio 2012
- GCC 3
- XCode on OS X
- Objective-c on Windows with GCC and Clang
- Macports GCC and Clang on OS X
    - Cross compilation for Linux and Windows
- Cygwin64
    - x86 and x64 binaries
    - mingw under cygwin
- Cygwin32
    - x86 and x64 binaries
    - mingw under cygwin

## Feature: Improved native source sets

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
- Model 'implicit' headers: in the same directory as the source files or included via relative path.
    - Need to make these available in the IDE and include in compile task inputs

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

## Story: Only use gcc/g++ front-ends for GCC and Clang tool chains

* Use 'gcc -x assembler -c' for assembly sources
* Use 'gcc' to link instead of 'g++': add -lstdc++ to linker args for C++ sources. Similar for objective-C++.
* Remove 'as' from the tools in the GCC tool chain.

## Story: Improve definition of Platform, Architecture and Operating System

In order to make it easier for users to publish and resolve native binary dependencies, this story introduces a set of conventional
Platforms, as well as improving the way that Platform components (Architecture, OperatingSystem) are defined. While it will still be possible
to define a custom Platform and build and publish binaries for that platform, the conventions introduced here should cover most common
platform variants that would be published and shared publicly.

This story also aggregates a bunch of review items that relate to Architecture and Operating System.

There are 2 main parts to the architecture that we are interested in: The CPU instruction set that is being used, and the data model. Usually,
but not always, a 32-bit processor instruction set is used with 32-bit data model, and a 64-bit processor instruction set is used with 64-bit
data model.

Usually, it is possible to combine different instruction sets in the same binary. So, when linking a binary targeting the amd64 CPU, it is fine to link
against a library built for the x86 CPU. It is not possible to combine different data models in the same executable.

There are two aspects of operating system that we are interested in: the operating system + version. The version is really a proxy for other attributes
we don't model yet: system calling convention, ABI version, language runtime version, which system APIs are available, and so on.

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
- For GCC, need to probe the output of the compiler to determine exactly what is being created
- For Clang, need to provide the full `-target <triple>` to define the exact output, or provide a subset and probe the output.
    - see http://clang.llvm.org/docs/CrossCompilation.html

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

Usually, it is fine to link a release build of a library into a debug binary, if the debug variant is not available. It is not fine to link a debug
build of a library into a release binary. On Windows, it is not ok to link a release build of a library into a debug binary.

On some platforms, additional artifacts are required to debug a binary. On gcc based platforms, the debug information is linked into the binary.
On Windows, the debug information is contained in a separate program database file (`.pdb`).

To generate the release binaries (default):

* Enable conservative optimisations.
* Compile with -DNDEBUG
* Compile with -g0

To generate the debug binaries (default):

* Disable optimisation.
* Compile with -g or /Zi
* Link with /DEBUG

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

## Story: Header-only libraries

### Use case

Producer project publishes a library consisting of header files only (e.g. a library of C++ template classes).

Consumer project compiles an executable against this library.

### Implementation

To implement this:

* Allow zero or more binaries for a given library at link and runtime. Also allow for zero or more header files.

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

## Open issues

- Change the GCC toolchain to use `gcc` to link when there is no C++ source included in a binary.
- Change the Clang toolchain to use `clang` to link when there is no C++ source included in a binary.
- Introduce `-lang` plugins for each native language.
- Statically type the tools settings on native binaries, rather than using extensions.
- A binary with no source should not be buildable.
- Need to split `Platform` into some immutable definition and some mutable configuration types.
- Should define standard platforms, architectures and operating systems, and allow these to be further specialized.
- Should understand 'debug' and 'release' build types, and allow these to be further specialized.
- May need some way to make toolchain a variant dimension?
- Incremental compile should handle `#import` with Visual C++, which may reference a `.tld` file.
- Incremental compile should not parse headers that we deem to be unchanging: 'system' libraries, unchanging repository libraries, etc.
- Add support for Clang on Windows
- Source set with srcdirs + patterns probably does not work for windows resource files
- For a static library, the .res file forms part of the library outputs.
- Add support for Windows resources with the GCC or Clang toolchains.
- Toolchains should provide the correct command-line args for resources-only library. Update sample.
- Generate and link a version resource by convention into every windows binary?
- `TargetedNativeComponent` should accept instances of `Platform`, `BuildType` and `Flavor`. Should also be able to query these.
- Cross-compilation for iOS.
- Modelling of frameworks for objective-c and toolchains understand how to provide these frameworks.
- Standard objective-c platform that provides the foundation framework.
- GCC toolchains allow path to be provided per platform?

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
- Tool chain represents an installation. Must be easy to configure the discovered tool chain, and configure extra installations.

## Source

- Allow dependencies to be declared between source sets
    - native language source set A depends on header source set B makes the headers of B available when compiling A.
    - native language source set A depends on native language source B makes the object files of B available when linking A into a binary.
    - header source set A depends on header source set B makes the headers of B available when compiling something that depends on A.
- Replace `SourceDirectorySet` with something that is actually a set of source directories.
    - Use this for sources and headers
    - Can access as a set of directories, a set of files or a file tree
    - Allow individual files to be added
    - Allow custom file extensions to be specified
- A source set may have its own dependencies, visible only to the source set
- When compiling, only the API of dependencies should be visible
- AssemblerSourceSet should implement DependentSourceSet (has source dependencies)
- Some conventional location for OS specific source for a component
- Allow encoding to specified on a source set, with default values as per java sources

## Compilation

- GRADLE-2943 - Fix escaping of compiler and linker args
- Understand and/or model -I/-L/-l/-D/-U
- Source set declares a target language runtime, use this to decide which driver to use to link
- Don't fail if g++ is not installed when it is not required
- Support preprocessor macros for assembler
- Clean the environment prior to invoking the visual studio tools (eg clear `%INCLUDE%`, `%LIB%` etc)
- Don't create compile tasks for empty source sets
- Compile windows resource files with gcc/clang using [`windres`](http://sourceware.org/binutils/docs/binutils/windres.html)

### Incremental compile

- Perform incremental compile when header is included via simple macro definition
- Keep a separate, build-scoped cache of file -> parsed includes. This would prevent need for reparsing per-variant and per-component.
- Detect changes to headers that are implicit on the include path via the tool chain

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
- Use separate directories for output binaries, rather than encoding all dimensions in the name: eg `flavor/platform/buildType/myLib.so`

## Toolchains

- Introduce `XCode`, `Cygwin`, `MinGW` toolchains, to allow selection of specific gcc or clang implementations.
    - Use the `XCode` tool chain to determine mac-specific gcc args
    - Use the `Cygwin` and `MinGW` toolchains to provide additional path requirements for `InstallExecutable` task
- Prevent configuration of tool chains after availability has been determined.

## Structure

- Some common plugin that determines which tool-chains to apply

### Language plugins

- Add a 'development' install task, which chooses a single variant for an executable to install
- Need to make standard 'build', 'check' lifecycle tasks available too. The `assemble` task should build all buildable variants.
    - Reasonable behaviour when nothing is buildable on the current machine.
- Come up with consistent naming scheme for language plugins: 'cpp', 'c', 'assembler', 'java-lang', 'scala-lang', etc
- Windows resources
    - Automatically add `/noentry` and `/machine` linker args when building resource-only DLL
    - Actually inspect the binary to determine if there are any exported symbols
    - Use a model rule to determine if library sources contain windows resources

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
- Integration test coverage for 64-bit assembler on all platforms/tool chains.
- Verify that the correct windows system libraries are available at link time
    - Use a test app that uses something like WriteFile() to write its hello world message to stdout. If it can link and run, then the paths are probably ok.

# Open issues

* Add options for seeing full tool command-line (like make -n)
* Add configuration hook for command-line tools to configure their environment
* For incremental build with visual c++, use `dumpbin  /RAWDATA` to strip timestamps from binary files before comparison
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
* Add support for cygwin-64 (uses a different data model to windows) and mingw under cygwin.
* Install task should generate a shell script as well as a batch script when running under cygwin.
* GCC and Clang under cygwin target the cygwin runtime, not the windows runtime.
* Add language level to C and C++ source sets.
* Adding other languages as external plugins.
* Consume dependencies from cocoapods repository.
* Publish to cocoapods repository.
* Consume dependencies from NuGet repository.
* Publish to NuGet repository.
* JNI plugin generates native header, and sets up the JNI stuff in $java.home as a platform library.
* Model minimum OS version.
    * For OS X can use -mmacosx-version-min option.
* Clean task for a binary
* Update CDT support to match Visual Studio support
* Rename 'install' task to indicate that it's installing a developer image
* Detangle knowledge about dealing with multiple source files from NativeCompiler
