This specification outlines the work that is required to use Gradle to create and use precompiled headers (PCH).

It's a part of the overall effort of improving C/C++/native build support within Gradle.


Support single file input for "precompiled header input"


# Use of Precompiled Headers

Precompiled headers can be used to speed up compilation time if a project uses large header libraries (e.g., Boost), uses header files that include many other header files or uses header files that are included by many source files.

Precompiled headers typically consist of a single header file that includes other header files to be compiled into an intermediate form for the compiler.  System headers and headers from external dependencies are usually good candidates for this technique, since they are unlikely to change.  A change to a precompiled header or any included header requires recompilation of all source units that include the precompiled header.

Precompiled headers must have the same compiler options as the source unit they are used with (or else bad things may happen). 

## Use Cases: Single Precompiled Header

There can only be one precompiled header per source unit compilation.  Some examples of using a precompiled header includes:

- I have a single project with a single native component and want to use a PCH with it.
- I have a single project with multiple native components and want to use the same PCH for multiple components.
- I have multiple projects with native components and want to use a PCH defined in one project across project boundaries.

## Uses Cases: Multiple Precompiled Headers

Using multiple precompiled headers for a single .c or .cpp file is unsupported by MSVC/GCC/Clang; however, a single native component may have multiple sets of sources that each have their own precompiled header. 

TODO: This seems very uncommon and may not fit with some IDE's view of a project.

# DSL (WIP)

General idea is to include the PCH as a property on a native source set (C/C++)

    model {
        components {
            mainExe(NativeExecutableSpec) {
                sources {
                    cpp {
                        precompiledHeader = file("path/to/my_pch.h")
                        source { }
                        headers { }
                    }
                }
            }
        }
    }

## Test Cases

- Build fails when developer defines precompiled header, but the precompiled header does not exist.  
- Build fails when developer defines PCH, but the PCH cannot be generated.
- Gradle warns when developer defines PCH, but the compiler cannot use it for some reason.
- When any file included in PCH header changes, PCH is rebuilt.
- Build produces a PCH for each variant and language combination (see Open Questions below).
- For GCC, build of PCH produces <precompiledHeader>.gch
- For MSVC/Clang, build of PCH produces <precompiledHeader>.pch
- For MSVC, Gradle looks for a <precompiledHeader>.cpp.  If it does not exist, we will generate one that #include's <precompiledHeader>.h

TODO: Flesh out steps for creating PCH for each toolchain.

- For all, build of component using PCH inclues PCH-build directory ahead of normal include path.
- For GCC, no other special arguments are needed.
- For Clang, build of component using PCH adds -include-pch for source files that have a dependency on the PCH.
- For MSVC, build of component using PCH adds /Yu<precompiledHeader>.pch for source files that have a dependency on the PCH.

Should be covered by existing infrastructure:
- Build of component using PCH executes when PCH changes.
- Build of component using PCH does not execute when PCH has not changed.

## Compiler Implementation Details

### GCC

TODO: Flesh out

Uses .gch extension for precompiled headers (e.g., my_pch.h.gch)

GCC will automatically use a precompiled header if it's found on the include search path first.

[Reference](https://gcc.gnu.org/onlinedocs/gcc/Precompiled-Headers.html)

### Clang

TODO: Flesh out

Similar to GCC, except uses .pch extension.

Clang will not automatically use a precompiled header unless it's included with -include or -include-pch.

[Reference](http://clang.llvm.org/docs/UsersManual.html#precompiled-headers)

### MSVC

Visual-C++ requires precompiled headers to be created from a source file (.c or .cpp).  Usually, the technique is to create a "boundary" header file (my_pch.h) and a corresponding source file (my_pch.cpp).

my_pch.cpp will consist of:

    #include "some_pch.h"
    #include "my_pch.h"
    #include "not_pch.h"
    // more code could go here

Command-line to create PCH:

    CL /Ycmy_pch.h my_pch.cpp 

The compiler will compile my_pch.cpp up until it reaches the boundary header file (my_pch.h) and produce a file my_pch.pch.  We could control that with /Fp.  Spaces are not allowed after MSVC arguments (e.g., '/Yc filename' is wrong).  This means the PCH source file could also be a "normal" source file and need to be compiled into an object file as well.

For source files that use the precompiled headers, you must compile with /Yumy_pch.h.  The compiler will skip processing anything in the source file before the include for my_pch.h and assume that my_pch.pch contains everything.  This usually means that my_pch.h should be the first file included in a source file.

Like GCC, Visual-C++ has a force include feature (/FI). 

[Reference](https://msdn.microsoft.com/en-us/library/b4w02hte.aspx)

## IDE Integration

### MSVC

TBD

# Open Questions

- Do we automatically add language source sets to a native component (i.e., applying c and cpp causes us to always generate a PCH for C and C++)?
- Does this extend well to "lump" or "unity" builds?
- Can we/should we try to generate precompiled header/source files given a list of headers to precompile? 
- Should we make use of -include or /FI to include precompiled headers in a given source set compilation? (aka prefix headers)
- What's the impact of the warning that "PCH files are machine dependant. Even with the same compiler, there is no way to package PCH for general use." that GCC and MSVC both have.
- Should probably be easy to turn off PCH for testing/diagnosing a broken build

# Other

- At least GCC can tell us if the precompiled header is actually used.  Is this useful info (at least for testing)?
- Not sure if we have all the info, but usage statistics on header files would give us some insight so we could provide recommendations for a PCH or automatic generation of a precompiled header.

# Out of Scope

TBD