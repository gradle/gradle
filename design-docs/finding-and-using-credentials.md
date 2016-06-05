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

## Story: An S3 repository can be configured to authenticate using AWS's EC2 instance metadata.

### Implementation

- Add the method org.gradle.internal.authentication.AuthenticationInternal.requiresCredentials to determine if an Authentication requires credentials
- change `org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory.validateConnectorFactoryCredentials` to only vaildate when credentials are required
- Add an authentication type `public interface AwsImAuthentication extends Authentication` and implementation `DefaultAwsImAuthentication extends AbstractAuthentication implements AwsImAuthentication`
- Change `org.gradle.internal.resource.transport.aws.s3.S3ConnectorFactory` to register `org.gradle.authentication.aws.AwsImAuthentication` as a supported authentication type
- Change `org.gradle.internal.resource.transport.aws.s3.S3ResourcesPluginServiceRegistry` to register the authentication scheme: `authenticationSchemeRegistry.registerScheme(AwsImAuthentication.class, DefaultAwsImAuthentication.class);`
- Add integration test support to stub out AWS IM http api calls.  

### Test coverage
- No instance metadata found on the host
- Successfully publish and resolve using IM authentication

### DSL

```
maven {
    url "s3://somewhere/over/the/rainbow"
    authentication {
        awsIm(AwsImAuthentication)
    }
}

ivy {
    url "s3://somewhere/over/the/rainbow"
    authentication {
        awsIm(AwsImAuthentication)
    }
}
```

## Story: S3 Repositories can be configured with credentials providers
A current shortcomming with gradle is the inability to model credentials providers. A credentilas provder is something which produces credentials and in most cases, sources those credentials from a particular location. There are several places from where credentials can be sourced: local file system, environment variables, the CLI, a network resource, etc. A credentials provider is responsible for locating (and or) producing one __type__ of crenentials only. The goal of this story is to retrofit the existing S3 resource/repository support to use credentials providers to configure AWS credentilas for S3 repositories. Two secondary aims of this story is to 1. provide the mechanics and a level of reuse such that credentials providers can be used to configure credentials for other repository types and 2. more generally, the same mechanism can be leveraged anywhere else in gradle where credentials may be required. #1 and #2 will not be achieved by this story alone and later stories will have to implement those features. 

## Implementation 

- Existing credentials and configuration must remain backward compatible. In this case `org.gradle.api.credentials.AwsCredentials` only.

- An `InternalCredentialsProvider<T extends Credentials>` is a credentials provider which provides credentials of type `T`. It has a single type of credentials it supports `T extends Credentials` and an accompaning public implementation of `org.gradle.api.credentials.CredentialsProvider`.

- An `AuthenticationProtocol` has a set of credentials types it supports (`Iterable<Class<? extends Credentials>>`) and zero or more `InternalCredentialsProvider`'s

- All implementations of `org.gradle.api.credentials.CredentialsProvider` are effectively the public facing types which can be configured via the build script DSL e.g. `credentials(AwsImCredentialsProvider)`

- Other implementations of `org.gradle.api.credentials.CredentialsProvider` can have properties which could themselves be credentials (e.g. `username = '---'`) but in general the properties of an `org.gradle.api.credentials.CredentialsProvider` should be about locating credentials. e.g
    - This is the endpoint where the credentials live
    - The private key is located at ~/.ssh/id_rsa'    

- An `AbstractAuthenticationSupportedRepository` can be configured with zero or more `org.gradle.api.credentials.CredentialsProvider`'s which are the types which a build author will have specified.

- A `org.gradle.internal.resource.connector.ResourceConnectorFactory` can have zero or more `AuthenticationProtocol`'s. This effectively determines all supported types of credentials and internal credentials provider implementations supported for a particular resource type. 

- A `org.gradle.internal.resource.connector.ResourceConnectorSpecification` can be configured with a set of `org.gradle.api.credentials.CredentialsProvider`'s which represents what the build author has configured. 

- With all of the above in place a `ResourceConnectorFactory` has enough to decide which authentication protocols the build author has specified and which `InternalCredentialsProvider` to use to source credentials.

- For an `S3ConnectorFactory` there will be a single authentication protocol `org.gradle.internal.authentication.AwsAuthenticationProtocol` which will have several `CredentialsProvider` implementations. e.g. `AwsImCredentialsProvider`, `AwsSessionTokenCredentialsProvider`, `AwsAnonymousCredentialsCredentialsProvider`, etc.


## Test coverge
- The error message for non supported `CredentialsProvider` types should list the supported types.
- Configuring credentials via both a credentials provider and the existing DSL produces an error and recommends using the `CredentialsProvider` way.
- S3 repos configured with `org.gradle.api.credentials.AwsCredentials` work as they do today.
- Integration test with the AWS instance metatdata service stubbed out
    + Returning an acces key, secrey key and a session token.
    + Returning an acces key and secrey key only.
    + No IM roles available which is a 404 not found response.

## DSL

```groovy

maven {
    url "s3://somewhere/over/the/rainbow"
    credentials(AwsImCredentialsProvider)
}

maven {
    url "s3://somewhere/over/the/rainbow"
    credentials(AwsAccessKeyCredentialsProvider){
        accessKey = '-----'
        secretKey = '-----'
    }
}

```

## Candidate Stories

* ~~Implement an authentication scheme to facilitate authenticating with S3 repositories using AWS EC2 Instance Metadata.~~
* Implement an authentication scheme to facilitate authenticating with S3 repositories using [temporary credentials](http://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html).
* Implement an authentication scheme to facilitate authenticating with S3 no credentials (publicly accessible buckets)
~~* Implement an authentication scheme to facilitate preemptive basic authentication with HTTP repositories.~~
* Implement an authentication scheme to facilitate public key authentication with SFTP repositories.
* Add a `CredentialsContainer` to `org.gradle.api.internal.project.DefaultProject` similar to `org.gradle.api.artifacts.ConfigurationContainer`
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
