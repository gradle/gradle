This specification is a proposal for the introduction of a top-level credentials container and some fundamental changes to
how repository transports and authentication mechanisms are modeled.

# Why?

* There has been an increase in the number of dependency management transports supported by Gradle (sftp, s3, ect.). One of the challenges identified in adding
 new transports is the lack of a suitable way to model and configure credentials for different transport mechanisms.

* Currently, the notion of credentials in Gradle are tied to repository implementations (`org.gradle.api.artifacts.repositories.AuthenticationSupported`), separating the mechanisms
 for providing credentials from the components that reply on credentials is going to allow more flexibility in terms of independently changing and implementing repository implementations.

* There is on-going work in progress to improve and encourage the re-use of `org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport` implementations. One of the goals of this work is to
make it easier to contribute new transport implementations from both within Gradle and via community plugins. A unified approach to modeling and configuring credentials will help achieve this goal.

# Use cases

There are many use cases where credentials and other secure information must be made available to Gradle:

* Resolving and downloading a Gradle runtime from a secure repository.
* Resolving dependencies from a secure repository.
* Publishing to a secure repository, including publishing plugins to `plugins.gradle.org`.
* Downloading script plugins from a secure repository.
* Signing artifacts. Often the signing material is encrypted in some form.
* Any custom task or build logic that uses a secure service in some form, for example:
    * Release automation that makes VCS changes.
    * Release automation that pushes artifacts or makes changes to remote systems.

Currently out of scope for this specification is any verification of artifacts received from some source, such as validating the signatures of the artifacts.
Also out of scope is any management of key material, though this of course could be automated by a plugin.

# Model

* A repository uses a _transport protocol_. This is the communications mechanism through which the resources of the repository are accessed, such as HTTP or SFTP.

* A transport protocol supports zero or more _authentication schemes_. An 'authentication scheme' represents how authentication is carried out. e.g.
    - Use EC2'S instance metadata service to authenticate with an AWS S3 repository.
    - Use basic, preemptive auth to authenticate with a repository over HTTPS.
    - Use public key authentication to authenticate with a repository over SFTP.

An authentication scheme is __not__ a transport protocol (HTTP, FTP, etc.)

* A particular repository will accept some subset of the authentication schemes supported by the transport protocol. For example, an SFTP server may be configured
to accept only public key authentication, and disallow password based authentication.

* A Gradle build user will accept some subset of the authentication schemes supported by the transport. For example, a user or build agent may require that basic HTTP auth
must not be used. Often, build users are not particularly opinionated regarding specific protocols - 'whatever works' is probably the most common opinion.

* Given this, a repository definition has associated with it a sequence of one or more authentication schemes in priority order.
    - When a repository transport is configured with more than one authentication scheme, each scheme should be attempted until one succeeds.

* An authentication scheme may accept zero or more _types of credentials_.
    - For our purposes, credentials are the input data provided by the build logic to the authentication scheme.
    - When the scheme does not accept any credentials it implies that the protocol implementation knows how to authenticate without any build configuration. e.g. AWS Instance Metadata.
    - When the scheme is configured with more than one type of credentials the scheme should attempt to authenticate using each type of credentials until one succeeds.

* An authentication scheme may accept zero or more _instances of_ the same type of credentials.
    - When an authentication scheme is configured with multiple instances of the same type of credentials the scheme should attempt to use each instance until one succeeds.

* A given type of credentials may be supplied from one or more sources, or _providers_. For example:
    - Encoded as data in the repository definition, eg username and password
    - Loaded from the `~/.ssh`
    - Provided by the operating system, eg the user's kerberos ticket or NTLM credentials.
    - Passed in to the build as system properties
    - User prompts in the IDE
    - Loaded from a Java keystore

## Transport details

* S3 transport:
    - Credentials: access key + secret key.
    - Use static credentials.
    - Use AWS credentials file.
    - Use EC2 instance credentials.
    - Use SDK defaults.
    - All use the same type of credentials, are different providers.
    - Can be implemented using various combinations of `AWSCredentialsProvider`.
* HTTP/HTTPS transport:
    - Basic auth: username + password.
    - Digest auth: username + password.
    - Kerberos: client-to-server ticket + client-to-server session key.
    - NTLM: domain, username, password.
    - Basic, Kerberos and NTLM can be performed preemptively.
    - Should replace existing username parsing with a public NTLMCredentials type.
    - Kerberos and NTML credentials can usually be provided by the environment (using native APIs or experimental support in http-client)
* SFTP transport:
    - Credentials: username + password.
    - Credentials: private-public key. May in turn require a password credential to use.
    - Authorized host keys.
* Plugin portal transport:
    - Credentials: api key + secret key.

# Stories

## Story: Build author configures the set of authentication schemes to use for an HTTP repository

- If user specifies one or more auth schemes for a repository, limit attempts to those schemes only.
- Where no auth schemes are configured, attempt all supported auth protocols for HTTP (current behaviour).
- Use the credentials supplied for the repository for all attempted authentication schemes
- Fail if an authentication scheme is specified that is not supported by the configured repository transport
    - Only define `BasicAuthentication` and `DigestAuthentication` schemes for now, which apply only to HTTP repository transports
    - All other repository transports disallow _all_ authentication schemes

```
    maven {
        url 'https://repo.somewhere.com/maven'
        credentials {
            username 'user'
            password 'pwd'
        }
        authentication {
            basic(BasicAuthentication)
            digest(DigestAuthentication)
        }
    }
```

### Implementation

- Remove the use of "all" scheme in HTTP Client, and explicitly configure the supported credential types when none defined by user
    - Investigate whether the Kerberos and SPNEGO authentication schemes currently work with Gradle.
      This will guide whether we should explicitly enable them in HTTP client
- Model an authentication scheme instance as having a single Credentials instance.
  We can later extend this to support multiple credentials for certain schemes.
    - Configure the HTTPClient by supplying with the configured `Authentication` instances.
- Order of configuration in `authentication { }` block is not significant: retain default ordering defined by HTTPClient (`org.apache.http.impl.client.AuthenticationStrategyImpl.DEFAULT_SCHEME_PRIORITY`)

### Test Coverage

- Can configure and resolve from HTTP repository using a single authentication scheme (others are _not_ attempted):
    - Basic Auth with password credentials
    - Digest Auth with password credentials
- Can configure and resolve from HTTP repository using Basic & Digest authentication schemes with common password credentials
    - Other schemes are _not_ attempted: can inspect the HTTP header for supported schemes
    - Will attempt Basic authentication only if Digest authentication fails, even if `BasicAuth` is specified first
- All supported authentication schemes for HTTP are attempted when none explicitly specified: existing test coverage may suffice
- Configuration failure when specifying:
    - authentication scheme for a repository with a transport other than HTTP/HTTPS
    - authentication scheme for a repository when no credentials have been specified
    - multiple authentication schemes of the same type
    - custom credentials type or AwsCredentials for an HTTP repository

### Out of scope

- Adding support or automated test coverage for NTLM, Kerberos or SPNEGO authentication
- Exposing an NTLM-specific credentials type
- Allowing credentials to be configured per authentication scheme
- Configuration of the order that authentication schemes are attempted

## Story: Build author configures Basic Auth to send credentials preemptively

- If repository is explicitly configured to use basic auth, preemptively send credentials

```
    maven {
        url 'https://repo.somewhere.com/maven'
        credentials {
            username 'user'
            password 'pwd'
        }
        authentication {
            basic(BasicAuthentication)
        }
    }
```

### Implementation

- Modify `PreemptiveAuth` request interceptor
    - Add basic credentials to _all_ requests when `BasicAuthentication` is specified
    - Use existing behavior (only on PUT and POST) when using defaults

### Test Coverage

- Can configure basic authentication to send credentials preemptively
    - Credentials are sent on all requests (including GET/HEAD)
- Can configure preemptive basic auth in conjunction with digest auth scheme
    - Should attempt digest auth if a 401 is received requesting basic auth

## Story: Build author configures repository for Windows authentication

- If user configures repository for windows authentication the appropriate HTTP auth schemes are enabled (NTLM, SPNEGO, Kerberos)

```
    maven {
        url 'https://repo.somewhere.com/maven'
        credentials {
            username 'user'
            password 'pwd'
        }
        authentication {
            windows(WindowsAuthentication)
        }
    }
```

### Implementation

- Investigate HttpClient support for NTLM w/o explicitly providing credentials

### Test Coverage

- Add coverage for NTLM based authentication, perhaps as provided by this [pull request](https://github.com/gradle/gradle/pull/444)

## Candidate Stories

* Implement an authentication scheme to facilitate authenticating with S3 repositories using AWS EC2 Instance Metadata.
* Implement an authentication scheme to facilitate preemptive basic authentication with HTTP repositories.
* Implement an authentication scheme to facilitate public key authentication with SFTP repositories.
* Add a `CredentialsContainer` to `org.gradle.api.internal.project.AbstractProject` similar to `org.gradle.api.artifacts.ConfigurationContainer`
* Credentials, for the most part, should represent where the credentials data lives e.g. 'the private key is located at ~/.ssh/id_rsa'
* `org.gradle.api.artifacts.repositories.AuthenticationSupported` must remain backward compatible to support the existing DSL for configuring repositories.
* Upgrade HttpClient to latest version, possibly leveraging improved NTLM support rather than current JCIFS implementation
* Add support for SNI (may simply come for free as part of HttpClient upgrade)

## Proposed DSL

```
credentials {
    awsAccessKeyCreds(AwsKeyCredentials) {
        accessKey = 'xxx'
        secretKey = 'xxx'
    }

    publicKeyCreds(PublicKeyCredentials) {
        username = 'x'
        privateKeyLocation = "~/.ssh/id_rsa"
    }

    basicAuthCreds(PasswordCredentials) {
        username = 'x'
        password = 'y'
    }

    secondaryBasicCreds(PasswordCredentials) {
        username = 'xx'
        password = 'yy'
    }
}


repositories {
    maven {
        url 's3://somewhere/bucket'
        //Multiple auth protocols
        authentication(AwsEnvironmentVarAccessKeyAuth)
        authentication(AwsAccessKeyAuth) {
            credentials(awsAccessKeyCreds)
        }
        //Auth protocol without credentials
        authentication(AwsInstanceMetadataAuth) {
            timeOut = 3000
        }
    }

    maven {
        url 'https://repo.somewhere.com/maven'
        //Auth protocol with credentials and configuration
        authentication(BasicAuth) {
            preemptive = true
            credentials(basicAuthCreds)
        }
    }
    maven {
        url 'https://repo.somewhere.com/maven'
        //Auth protocol with credentials and configuration
        authentication(BasicAuth) {
            preemptive = true
            credentials {
	            username 'foo'
	            password 'bar'
            }
        }
    }

    maven {
        url 'https://repo.somewhere.com/maven'
        //Auth protocol with multiple credentials
        authentication(BasicAuth) {
            credentials(basicAuthCreds)
            credentials(secondaryBasicCreds)
        }
    }

    maven {
        url 'sftp://someDrive/maven'
        authentication(PublickeyAuth) {
            credentials(publicKeyCreds)
        }
    }
}
```
