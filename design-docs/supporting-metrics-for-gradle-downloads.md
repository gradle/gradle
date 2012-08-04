
# Use cases

We want to be able to collect some metrics about which tools are being used to download resources from
the Gradleware repositories. These metrics will be used partly to get some idea of how many people
are using Gradle, and which versions of Gradle and associated tooling they are using.

We must not capture any information that can be used to indentify individuals or organisations.

The use cases we're interested in:
* The wrapper or tooling API or migration tool downloads a Gradle distribution.
* A build downloads the tooling API jars.


# User visible changes

The proposal is to capture the following information:
* The tool that is being used to download the resource (wrapper, Gradle, tooling API, etc).
* The version of the tool.
* The version of Java.
* The operating system.

This information will be sent in the User-Agent header of HTTP requests made by Gradle and tools.

## Sad day cases

Not applicable. The implementation covers adding an additional header to HTTP requests we already make. No changes to how we deal with sad day (or not)
will be made.

# Integration test coverage

* Change the existing integration test that verifies that the wrapper can download a remote HTTP distribution, to assert that the appropriate
  header is included in the request.
* Change the existing intergration test that verifies that Gradle can download artifacts from an HTTP Ivy repository, to also assert that the
  appropriate header is included in the request.
* Change the existing intergration test that verifies that Gradle can download artifacts from an HTTP Maven repository, to also assert that
  the appropriate header is included in the request.
* Change the existing intergration test that verifies that Gradle can download a remote HTTP build script, to also assert that the appropriate
  header is included in the request.
* Add an integration test case to verify that the tooling API can download a remote HTTP distribution, and assert that the appropriate header
  is included in the request.

# Implementation approach

For artifacts downloaded from an Ivy or Maven repository, the User-Agent header is already being populated. We need to add the additional information
about the JVM and OS.

For remote scripts, we will need to add the User-Agent header.

For distributions downloaded by the wrapper and tooling API, we will need to add the User-Agent header. The wrapper does not have any idea of its version.
There are 2 potential approaches:

1. Include the `build-receipt.properties` resource in the wrapper jar and add some code to the wrapper to parse this.
2. Move the GradleVersion class to the base-services project, and have the wrapper project bundle the base-services into the wrapper jar, as it
   currently does for the cli project. At this point, we should use jarjar to strip unused classes from the wrapper jar, as there are many classes
   in base-services that we don't want to include.

# Open issues

* Should we start using the HTTP resource implementation for downloading remote scripts, given that we have recently put a lot of effort into detangling the resource implementation from Ivy?
This would have a few advantages: we'd pick up the User-Agent changes automatically, we'd get progress logging for downloading these scripts, and it would then
be relatively simple to add caching and --offline support.

* Should we start using the HTTP resource implementation for downloading distributions from the tooling API, instead of the java.net.URL implementation it inherits
from the wrapper? Currently, there is not a compelling reason to do this, and the downside would be that this would drag a bunch of extra dependencies into
the tooling API (and so, into the embedding application).
