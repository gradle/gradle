Support for NTLM authentication is critical in an enterprise environment. We currently have no test coverage for our support for NTLM authentication,
 either to a repository or to an authenticating proxy server. This means that NTLM authentication can (and does) break without our knowledge,
 leading to issues that need to be resolved during the RC phase, or worse.

The goal is to have a test environment that can be used to verify our support for NTLM authentication. Ideally, this test environment should be able to evolve
into a full "standard enterprise" environment, including a Windows client, Windows server, and standard Windows domain/network services.

Once we have some basic test coverage in place, we can begin to improve our NTLM support.

# Use cases

In each use case, the 'standard enterprise environment' includes:
- Server machine running Windows Server with a NTLM authenticated HTTP proxy and an NTLM authenticated corporate repository
- Developer workstation running Windows with all internet access directed through the corporate proxy server

1. Developer installs Gradle and executes build that resolves dependency in MavenCentral in standard enterprise environment.
2. Developer installs Gradle and executes build that resolves dependency in corporate repository.
3. Developer installs Gradle and executes build that publishes module to standard corporate repository.
4. Developer executes wrapper build where wrapper downloads Gradle version from services.gradle.org in standard enterprise environment.
5. Developer executes wrapper build where wrapper downloads customised Gradle version from corporate repository in standard enterprise environment.
6. Developer installs Gradle and executes build that applies external script file that is downloaded from the internet in standard enterprise environment.

# Implementation plan

## Test coverage for generic client resolving dependencies from MavenCentral via an NTLM authenticated proxy server

Here we introduce the _server_ portion of the 'standard enterprise environment', and a simple smoke test that will use this as a proxy server.
Due to licensing restrictions, we are unlikely to be able to utilise a reusable virtual machine instance for running Windows Server, so instead we'll probably need
to provision a shared instance that can be used by both developer builds and TeamCity builds.

The _client_ portion of the 'standard enterprise environment' is left for a later story.

### Implementation approach

1. Investigate licensing options for virtualization of Windows Server instances.
2. Create and configure a single Windows Server instance for tests to utilize. Use a virtual instance if possible.
    - If it is feasible, use Vagrant (or similar) to configure and initialise a virtualised Windows Server instance for test execution.
    - If not possible, create and configure a single shared instance for all tests to utilise.
3. Configure the Windows Server with IIS serving as an NTLM authenticated proxy server to access the internet, and a single permitted user.
    - Restrict this proxy server so that only MavenCentral is accessible.
4. Create integ tests that executes a build that resolves dependencies from MavenCentral, hard-coded to use the NTLM authenticated proxy server.
    - Test should be run on all standard platforms.
    - Correct proxy credentials should not be hard-coded into the test or anywhere in the codebase (supplied via a build property)
    - Test should be ignored if proxy credentials are not available.

### Test coverage

- Execute build that downloads dependency from MavenCentral
    - User name set as DOMAIN\NAME and just name
    - Domain value: [empty | set via "DOMAIN\NAME" | set via "http.auth.ntlm.domain"]
    - Workstation value: [empty | set via "http.auth.ntlm.workstation"]
- Manually verify that this test would have caught NTLM issues in Gradle-1.0-milestone-7, Gradle-1.4-rc-1

### Sad day cases

- Invalid http proxy settings: check useful error message
    - Bad user or password
    - Bad domain or workstation
- Module does not exist in MavenCentral: check correct error message

## Test coverage for generic client resolving dependencies from NTLM authenticated repository

1. Configure the 'standard enterprise server' with IIS so that it is able to serve up a simple ivy repository. Access to the ivy repository should be restricted by NTLM authentication.
    - Add a simple, fixed module to the repository
2. Extend the NTLM integ test to also resolve dependency from this well-known repository.

## Allow NTLM auth implementation to be selected via 'http.auth.ntlm.impl' system property

1. If 'http.auth.ntlm.impl' is set to 'httpclient', do _not_ register the custom JCIFS-backed NTLM scheme for authentication.
2. Default value of system property is 'jcifs': continue to register the custom scheme for this value.
3. Fail to initialise HttpClient for any other value, with a sensible exception message.
4. Where NTLM authentication fails, include option of setting system property to 'httpclient' in recommended things to try.
5. Upgrade to HttpClient 4.3.x: these versions have improved NTLM support built-in
6. If tests pass, switch default value of 'http.auth.ntlm.impl' to 'httpclient'. Continue to support 'jcifs'.

### Test coverage

- Test NTLM proxy authentication with "http.auth.ntlm.impl" values ['httpclient', 'jcifs']

### Sad day cases
- Verify useful error message with "http.auth.ntlm.impl" set to invalid value
- Verify that system property setting is recommended when NTLM proxy auth fails

## Test coverage for publishing to NTLM authenticated repository

## Test coverage for downloading wrapper via NTLM authenticated proxy

## Smoke test for standard corporate developer installing Gradle from distribution and executing simple build.

This story introduces a 'standard enterprise developer workstation' concept, together with a very basic smoke test.
- The 'workstation' used for testing will be a virtualised Windows instance, with configuration scripted via Vagrant/Puppet/Chef.
- The default 'workstation' will be:
    - a virtualised Windows instance, with configuration scripted via Vagrant/Puppet/Chef
    - a clean install of Windows with a JDK
    - have standard security settings for a corporate desktop, using the 'standard enterprise server' for all network services
    - force all internet access via the corporate proxy server.

The smoke test will involve installing Gradle from an available distribution, and launching a simple Gradle project that does not have any remote dependencies.

## Extend the 'enterprise server' test coverage to utilise the 'standard enterprise workstation' as the Gradle hosting machine

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.
