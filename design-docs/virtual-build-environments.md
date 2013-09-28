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

# Open issues

* How can we keep VMs secure, in particular if they run insecure or outdated OSes (e.g. Windows XP)?
* How do we deal with Windows OS updates, in particular security updates?
* How do we allocate VMs to physical machines? Do all machines run the same number and type of VMs, or do we have specialized machines (e.g. Linux VMs vs. Windows VMs)?


