# Introduction

As Gradle continues to support more languages, technologies, and platforms, it will need to be built, tested, and developed
in an ever growing set of environments. To keep up with this trend, we want to automate the setup and management
of these environments. So far, we have identified the following requirements and use cases:

* Simplify administration of windows CI servers
* Allow a more heterogenous CI server environment
* Ensure consistency across CI server environment
* Make it easy to add new CI servers
* Can add physical machines with different architectures
* CI environment is secure and reliable
* Make it easy for a developer to run builds in different environments
* Permit testing NTLM authentication
* Test native binaries on target platform not build platform

As a first step, we want to virtualize CI build environments. Later, we may also want to virtualize dev environments
(e.g. for devs working on Windows C++ support), and may want to have a way to dynamically create environments required
by certain tests (e.g. the Gradle build could set up and tear down environments as needed). Note that with the first step
implemented, developers will already be able to run private builds in any CI build environment.

# Implementation plan

## Used technologies

For server virtualization, [KVM](http://www.linux-kvm.org/) will be used. (At some point, we might want to combine KVM with Linux
containers in order to have a light-weight way to isolate Linux environments.) As a configuration management tool,
[Salt](http://saltstack.com) will be used.

## Automate base installation of a CI machine

The base installation will be Ubuntu 12.04 LTS with a KVM hypervisor and a Salt minion. The configuration steps to automate will be similar
to what's currently documented on the Wiki page for setting up dev servers. The base installation should be as minimal as possible.

## Automate installation of an Ubuntu VM on a CI machine

Adding an Ubuntu VM to a CI server should be easy and repeatable. The installation should include the following software:

* Salt minion
* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* GCC 3 and 4 (may require separate VMs or Linux containers)
* Clang

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

## Automate installation of a Windows VM on a CI machine

Adding a Windows VM to a CI server should be easy and repeatable. The installation should include the following software:

* Salt minion
* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* Visual C++ 2010 express
* Microsoft Windows SDK 7
* Cygwin-32 with the following packages:
    * gcc
    * gcc-g++
    * clang
* MinGW

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

## Automate installation of a Mac OS X VM on a CI machine

Adding a Mac OS X VM to a CI server should be easy and repeatable. The installation should include the following software:

* Salt minion
* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* XCode v4+ with 'Command Line Tools' component

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

## Managing and monitoring VMs

As far as possible, Salt's KVM module should be used to manage VMs. Initially, it will be good enough to monitor
availability of VMs via TeamCity's agent page.

## Testing native binaries

For integration testing the native plugins, we need a way to:

* Build a test binary on M *host platforms*
* For each host platform, test the produced binary on N *target platforms*
* Collect the results and report on them (e.g. build passed/failed)

A host platform is a particular toolchain on a particular OS. Which target platforms
to test a binary on is a function of the particular test (suite) and host platform.
Testing a binary means executing it and collecting its standard output/error and exit code.

We can solve this by:

* Having a ("static", i.e. long-running) build VM and TC agent for each host platform and target platform
* Having a TC job per host platform that:
  * Builds a test binary for this platform
  * Distributes the binary to all compatible (or desired) target platforms (using Salt)
  * Executes the binary on these target platforms (using Salt)
  * Collects the results of running the binaries (using Salt)
  * Inspects the results and fails the build if problems are found

The TC job can be implemented as a Gradle build. The individual steps could be Gradle tasks
or (one and the same) JUnit/Spock integration test. For distributing, executing, and collecting
results of running binaries, we can use Salt's rich targeting and remote execution capabilities,
which make it possible to distribute files and execute commands between all build VMs.

### Host Platforms

We have identified the following host platforms:

* GCC 3 + old Linux
* GCC 4.0 + new Linux
* GCC 4.latest + new Linux
* Same for Clang + Linux (earliest supported Clang + latest Clang)
* Windows XP + MinGW + gcc 3
* Windows XP + Visual Studio 10 (2010)
* Windows 7 (8?) + MinGW + gcc 4.latest
* Windows 7 (8?) + Visual Studio 12 (2012)
* WinXP + cygwin (32bit) + gcc 3/4
* WinXP + cygwin (64bit) + gcc 3/4
* MinGW/cygwin + Clang

Many of these don't require separate VMs. For example, currently we do WinXP + VS10 +
MinGW (gcc 4) + cygwin-32 (gcc 4) on the same VM. On Linux, we could
also use Linux containers (rather than full-blown VMs) to separate environments.

We should start out with a minimal set of host platforms, and gradually
add new platforms over time.

### Target Platforms

We have identified the following target platforms:

* WinXP
* Windows 7 (8?)
* Probably some windows server versions
* Linux on a 32 bit processor, or maybe just 32 bit linux
* 64bit linux

The target environments should *not* have the development tools installed, if possible.
It's easy to produce a binary that runs on a dev machine but not on a clean machine.

We should start out with a minimal set of target platforms, and gradually
add new platforms over time.

### Host/Target platform combinations

We don't need to test the full matrix of host and target platforms. For many tests, host 
and target platform will be the same. Some tests will have a single target platform 
(lowest common denominator?), and some will have multiple.

For Windows, we could do:
* win* -> 32 bit windows
* win.latest[mingw/cygwin/visualcpp] -> win.latest

Similar for Linux: We test all hosts building for a common target, then one host building for a range of targets.

# Open issues

* How can we keep VMs secure, in particular if they run insecure or outdated OSes (e.g. Windows XP)?
* How do we deal with Windows OS updates, in particular security updates?
* How do we allocate VMs to physical machines? Do all machines run the same number and type of VMs,
  or do we have specialized machines (e.g. Linux VMs vs. Windows VMs)?


