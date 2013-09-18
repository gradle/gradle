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

## Automate base installation of a CI server

Regardless of the virtualization technology used, a hypervisor will have to be installed on each CI server.

## Automate installation of an Ubuntu VM on a CI server

Adding an Ubuntu VM to a CI server should be easy and repeatable. The installation should include the following software:

* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* GCC 3 and 4 (may require separate VMs or Linux containers)

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

## Automate installation of a Windows VM on a CI server

Adding a Windows VM to a CI server should be easy and repeatable. The installation should include the following software:

* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* Visual C++ 2010 express
* Microsoft Windows SDK 7
* Cygwin
* MinGW

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

## Automate installation of a Mac OS X VM on a CI server

Adding a Mac OS X VM to a CI server should be easy and repeatable. The installation should include the following software:

* TeamCity agent
* Oracle JDK 5, 6, 7 (perhaps also 8 developer preview)
* OpenJDK 5, 6, 7
* IBM JDK 5, 6, 7
* XCode v4+ with 'Command Line Tools' component

It should be possible to create variations of the installation recipe, e.g. to add VMs that have different software installed.

# Open issues

* Do we bake our own VM images, or is it good enough to automate the VM/OS/software installation process?
* How do we control the lifecycle of VMs? Which VM management/monitoring solution do we use?
* How can we keep VMs secure, in particular if they run insecure or outdated OSes (e.g. Windows XP)?
* How do we deal with OS updates, in particular security updates?
* How do we allocate VMs to physical machines? Do all machines run the same number and type of VMs, or do we have specialized machines (e.g. Linux VMs vs. Windows VMs)?


