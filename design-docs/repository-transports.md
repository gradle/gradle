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

## Support publishing to a Maven or Ivy SFTP repository using legacy publishing plugins

## Support publishing to a Maven or Ivy AWS S3 repository using legacy publishing plugins

## Make it easier to test a repository transport implementation

- Introduce an integration test suite that tests ResourceConnector directly: run the tests against each ResourceConnector implementation.
    - Each resource connector project will provide a fixture that allows it to be executed against the ResourceConnector test suite
    - Should include specific transport tests, together with some publish/resolve smoke tests
- Remove coverage of different transports from dependency management integration suite

## Support 'scp' scheme for ivy and maven repository URL

## Use public key authentication when accessing sftp/scp/https repository

Provide some way in the repository DSL to specify that public key authentication be used for any transport that
supports it. For sftp and scp, provide a way to use the user's SSH settings.

## Apply build scripts from an sftp/scp/s3 URL

Generalise the repository transports so that they can be reused to download build scripts. This will add sftp/scp/s3 support.

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

