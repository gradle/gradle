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

- At the moment we can't differentiate between "password auth not supported" and "invalid credentials". 
    - The ssh library we use (Jsch + underlaying apache sshd library) does not expose this information.
- Add an `AbstractMultiTestRunner` implementation to permit a test to be run with different repository transports.

## Support resolving from a maven repository declared with 'sftp' as the URL scheme, using password credentials

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
- Resolve dynamic version from maven repository with 'sftp'
- Reasonable error message produced when:
    - attempt to resolve missing module with valid credentials
    - resolve with invalid credentials
    - resolve where cannot connect to server
    - resolve where server throws exception

## Support resolving from a Ivy/Maven repository backed by AWS S3

### User visible changes

    repositories {
        maven {
            url "s3://bucket/repo-path"
            credentials(AwsCredentials) {
                accessKey "someKey"
                secretKey "someSecret"
            }
        }
    }

### Implementation plan

- ~~Basic support for `Credentials` other than `PasswordCredentials`: `AwsCredentials`~~
- ~~`AuthenticationSupported.getCredentials(Class<? extends Credentials>)` provides a credentials instance of the appropriate type~~

    - ~~Instantiates a new credentials of that type if no credentials is configured~~
    - ~~Hard coded factory method can create only `AwsCredentials` and `PasswordCredentials`~~
    - ~~Returns existing credentials of that type~~
         - ~~Fails if existing credentials is of a different type~~
         - ~~Fails for credentials of unknown type~~
    - `AuthenticationSupported.credentials(Class<T extends Credentials>, Action<? super T>)` configures credentials of the specified type~~
         - ~~Creates credentials on demand if required~~
         - ~~Fails if existing credentials have a different type~~
         - ~~Fails for credentials of unknown type~~
    - ~~Existing untyped credentials methods on `AuthenticationSupported` simply call the typed methods with `PasswordCredentials.class`~~

### Test cases

- Resolve from Maven & Ivy repositories
- Reasonable error message produced when resolving:
    - S3 Repository with empty credentials
    - S3 Repository with `PasswordCredentials` (error may be thrown when configuring repository)
    - S3 Repository with incorrect credentials
    - Resource not found

### Open issues

- Mechanism to register different types of `Credentials`
- Add common test suites for different repository protocols

## Support publishing to a Maven repository backed by AWS S3

## Support publishing to a Ivy repository backed by AWS S3

## Support publishing to a Maven repository declared with 'sftp' as the URL scheme, using password credentials

### Test cases

In many cases, this may be a matter of adapting existing test coverage to run against multiple transports.

- Un `@Ignore` `MavenPublishSftpIntegrationTest`.
- Publish via 'sftp' to maven repository (old and new plugins)
- Reasonable error message produced when:
    - publish with invalid credentials
    - publish where cannot connect to server
    - publish where server throws exception

## Make it easier to add a repository transport implementation

- Add a ResourceConnector API that can be used as the interface between dependencyManagement and a custom transport.
    - Similar to `S3ResourceConnector`
    - Create an implementation for 'http', 'https', 'sftp', 'file', 's3': none of these should live in ':dependencyManagement'
    - This API will be internal for now.
- Remove the dependency of 'dependencyManagement' subproject on the various resource subprojects.
    - The dependency management project will create a ResourceConnectorRegistry, and each resource subproject will register their ResourceConnector (factory).
    - Replace the existing `RepositoryTransport` implementations with a single implementation backed by `ResourceConnector`
