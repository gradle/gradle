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

## Support publishing to a Maven or Ivy SFTP repository using legacy publishing plugins

## Support publishing to a Maven or Ivy AWS S3 repository using legacy publishing plugins

## Make it easier to add a repository transport implementation

- Add a ResourceConnector API that can be used as the interface between dependencyManagement and a custom transport.
    - Similar to `S3ResourceConnector`
    - Create an implementation for 'http', 'https', 'sftp', 'file', 's3': none of these should live in ':dependencyManagement'
    - This API will be internal for now.
- Remove the dependency of 'dependencyManagement' subproject on the various resource subprojects.
    - The dependency management project will create a ResourceConnectorRegistry, and each resource subproject will register their ResourceConnector (factory).
    - Replace the existing `RepositoryTransport` implementations with a single implementation backed by `ResourceConnector`

## Make it easier to test a repository transport implementation

- Introduce an integration test suite that tests ResourceConnector directly: run the tests against each ResourceConnector implementation.
    - Each resource connector project will provide a fixture that allows it to be executed against the ResourceConnector test suite
    - Should include specific transport tests, together with some publish/resolve smoke tests
- Remove coverage of different transports from dependency management integration suite
- Verify artifact reuse

## All repository transports support using `sha1` resources to avoid downloads

Currently only the HTTP transports support using for a `.sha1` resource.

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

## org.gradle.api.resources.Resource implementation backed by transport infrastructure

