This specification is a proposal for the introduction of a top-level credentials container and some fundamental changes to
how repository transports, and they're authentication mechanisms, are modeled

# Why?

* There has been an increase in the number of dependency management transports supported by Gradle (sftp, s3, ect.). One of the challenges identified in adding
 new transports is the lack of a suitable way to model and configure credentials for different transport mechanisms.

* Currently, the notion of credentials in Gradle are tied to repository implementations (`org.gradle.api.artifacts.repositories.AuthenticationSupported`), separating the mechanisms
 for providing credentials from the components that reply on credentials is going to allow more flexibility in terms of independently changing and implementing repository implementations.

* There is on-going work in progress to improve and encourage the re-use of `org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport`'s. One of the goals of this work is to
make it easier to contribute new `RepositoryTransport`'s from both within Gradle and via community plugins. A unified approach to modeling and configuring credentials will help achieve this goal.

# Stories
* A repository transport is configured with zero or more authentication protocols. An 'authentication protocol' represents how authentication is carried out. e.g.
 - Use EC2'S instance metadata service to authenticate with an AWS S3 repository.
 - Use basic, preemptive auth to authenticate with a repository over HTTPS.
 - Use public key authentication to authenticate with a repository over SFTP.
An authentication protocol is __not__ a transport protocol (HTTP, FTP, etc.)
When a repository transport is configured with more than one authentication protocol each protocol should be attempted until one succeeds.


* An authentication protocol may accept zero or more __types of credentials__.
 - When the protocol does not accept any credential types it implies that the protocol implementation knows how to authenticate without any build configuration. e.g. AWS Instance Metadata.
 - When the protocol is configured with more than one type of credentials the protocol should attempt to authenticate using each type of credentials until one succeeds.

* An authentication protocol may accept zero or more __instances of__ the same type of credentials.
 - When an authentication protocol is configured with multiple instances of the same type of credentials the protocol should attempt to use each instance until one succeeds

* Implement an authentication protocol to facilitate authenticating with S3 repositories using AWS EC2 Instance Metadata
* Implement an authentications protocol to facilitate preemptive basic authentication with HTTP repositories
* Implement an authentications protocol to facilitate public key authentication with SFTP repositories


# Implementation Plan

* Add a `CredentialsContainer` to `org.gradle.api.internal.project.AbstractProject` similar to `org.gradle.api.artifacts.ConfigurationContainer`
* Credentials, for the most part, should represent where the credentials data lives e.g. 'the public key is located at ~/.ssh/id_rsa.pub'
* `org.gradle.api.artifacts.repositories.AuthenticationSupported` must remain backward compatible to support the existing DSL for configuring repositories and they're credentials.


## Proposed DSL

```
credentials {
    awsAccessKeyCreds(AwsKeyCredentials) {
        accessKey = 'xxx'
        secretKey = 'xxx'
    }

    publicKeyCreds(PublicKeyCredentials) {
        publicKeyLocation = "~/.ssh/id_rsa.pub"
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
