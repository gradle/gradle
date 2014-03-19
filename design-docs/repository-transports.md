Currently, the 'maven' and 'ivy' repository implementations only support publishing and resolving via HTTP, HTTPS and local file system.
To use SFTP, SCP or any other protocol, users currently need to revert to using a (deprecated) custom Ivy resolver.
It would be great to add native support for different protocols to the 'ivy' and 'maven' repositories.
This would involve adding support for dependency resolution, as well as for publishing via the new publishing plugins.

# Use cases

* Publish to and resolve from a repository via sftp, supplying user/password credentials
* Publish to and resolve from a repository via scp, supplying user/password credentials
* Publish to and resolve from a sftp/scp/https repository, using public key authentication
* Cache build scripts applied from an http/https URL
* Apply build scripts from an sftp/scp URL
* Prompt user for credentials when not provided in the repository definition

# Stories

## Support an ivy repository declared with 'sftp' as the URL scheme, using password credentials

This story allows an ivy repository to be declared with an 'sftp' scheme, with credentials supplied explicitly via the credentials DSL.

### User visible changes

Configuring a repository for sftp transport:

    repositories {
        ivy {
            credentials {
                username 'testuser'
                password 'password'
            }
            url 'sftp://my.host.com/path/to/repo'
            layout 'maven'
        }
        ivy {
            credentials {
                username 'testuser'
                password 'password'
            }
            ivyPattern 'sftp://my.host.com:4444/path/to/repo/[module]/[revision]/ivy.xml'
            artifactPattern 'sftp://my.host.com:4444/path/to/repo/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]'
        }
    }

### Implementation

- Add new `org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport` implementation for sftp.
    - Provide implementations of `ExternalResourceAccessor`, `ExternalResourceLister` and `ExternalResourceUploader`
    - Use a `PasswordCredentials` instance for authentication information.
- Change `RepositoryTransportFactory` so that it provides a transport based on a set of schemes, and an optional `PasswordCredentials` instance.
    - This will replace the logic in `org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.createResolver`
- Add support for the 'sftp' scheme to `RepositoryTransportFactory`

### Test coverage

Probably use the existing SFTPServer fixture for these, together with `IvyFileRepository` to configure and verify the repository content.

- Resolve via 'sftp' from ivy repository.
    - with default ivy layout
    - with 'maven' layout, using group == 'org.group.name'
    - with custom 'pattern' layout (with and without m2Compatible)
    - with multiple `ivyPattern` and `artifactPattern` configured
- Publish via 'sftp' to ivy repository
    - with default ivy layout
    - with 'maven' layout, using group == 'org.group.name'
    - with custom 'pattern' layout (with and without m2Compatible)
    - with multiple `ivyPattern` and `artifactPattern` configured
- Resolve dynamic version from ivy repository with 'sftp'
- Reasonable error message produced when:
    - attempt to resolve missing module with valid credentials
    - publish or resolve with invalid credentials
    - publish or resolve where cannot connect to server
    - publish or resolve where server throws exception

Note that the coverage described for successful publish and resolve is greater than we currently have for HTTP or File transports.
The plan will be to include this coverage for HTTP transport in a later story.

### Open issues

## Verify server interactions for 'sftp' publish and resolve

### Implementation

- Modify the `SFTPServer` fixture so that call expectations can be explicitly configured
    - Extract a `RemoteServer` API with call expectation configuration out of `HttpServer`
    - Extract `IvyRemoteRepository` and `IvyRemoteModule` out of `IvyHttp*`, such that they deal in terms of `RemoteServer`.
- Adapt the 'sftp' integration test coverage to use `IvyRemoteRepository`, verifying the server interactions.
- Adapt the 'sftp' integration tests so that they can be run against both sftp and http repository instances
    - Multiple test subclasses would be sufficient.

### Open issues

- Add an `AbstractMultiTestRunner` implementation to permit a test to be run with different repository transports.

## Support a maven repository declared with 'sftp' as the URL scheme, using password credentials

### User visible changes

Configuring a repository for sftp transport:

    repositories {
        maven {
            credentials {
                username 'testuser'
                password 'password'
            }
            url 'sftp://my.host.com/path/to/repo'
        }
    }

### Test cases

In many cases, this may be a matter of adapting existing test coverage to run against multiple transports.

- Resolve via 'sftp' from maven repository.
- Publish via 'sftp' to maven repository (with maven-publish)
- Resolve dynamic version from maven repository with 'sftp'
- Reasonable error message produced when:
    - attempt to resolve missing module with valid credentials
    - publish or resolve with invalid credentials
    - publish or resolve where cannot connect to server
    - publish or resolve where server throws exception

## Run more remote publish and resolve integration tests against an sftp repository

Adapt more existing test coverage to execute against an 'sftp' repository:

- Most of the tests in `org.gradle.integtests.resolve.ivy`
- Most of the tests in `org.gradle.integtests.resolve.maven`
- `org.gradle.api.publish.ivy.IvyPublishHttpIntegTest`
- `org.gradle.api.publish.ivy.IvyPublishMultipleRepositoriesIntegTest`
- `org.gradle.api.publish.maven.MavenPublishHttpIntegTest`

## Support 'scp' scheme for ivy and maven repository URL

## Use public key authentication when accessing sftp/scp/https repository

Provide some way in the repository DSL to specify that public key authentication be used for any transport that
supports it. For sftp and scp, provide a way to use the user's SSH settings.

## Apply build scripts from an sftp/scp URL

Generalise the repository transports so that they can be reused to download build scripts. This will add sftp/scp support.

* Add some way to provide credentials, possibly by modelling as a plugin repository.

## Cache build scripts that are applied from an remote URL

Generalise the remote resource caching layer so that it can be reused for build scripts.

* Check for changes periodically, with command-line override to check now.
* Offline support.

## Prompt command-line user for credentials when not provided

Provide some general credentials management service, plus a provider implementation that can prompt on the command-line. Use this to ask for credentials when attempting to
use a remote resource that requires authentication, and where no credentials have been provided.

## Prompt IDE user for credentials when not provided

Allow tooling API clients to provide a credentials provider. This will allow IDE integrations to prompt the user for and manage their credentials.

