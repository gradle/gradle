This specification is a proposal for the introduction of a top-level credentials container.

# Why?

* There has been an increase in the number of dependency management transports supported by Gradle (sftp, s3, ect.). One of the challenges identified in adding
 new transports is the lack of a suitable way to model and configure credentials for different transport mechanisms.

* Currently, the notion of credentials in Gradle are tied to repository implementations (`org.gradle.api.artifacts.repositories.AuthenticationSupported`), separating the mechanisms
 for providing credentials from the components that reply on credentials is going to allow more flexibility in terms of independently changing and implementing repository implementations.

* There is on-going work in progress to improve and encourage the re-use of `org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport`'s. One of the goals of this work is to
make it easier to contribute new `RepositoryTransport`'s from both within Gradle and via community plugins. A unified approach to modeling and configuring credentials will help achieve this goal.

# Use Cases
* Provide specific credential types to support authenticated components.
* Make it easy for build authors and plugin developers to add new Credential types.
* Provide a DSL for defining credentials at the project level.
* Enhance the existing repository transports to work with these new Credential types.

# Implementation Plan

* Provide a number of credential types which model, at a minimum, the credentials used by existing transports within Gradle. Each one should implement `org.gradle.api.credentials.Credentials`
  * PasswordCredentials
  * AwsCredentials
   * AwsKeyCredentials
   * AwsIMCredentials
  *  PublicKeyCredentials
  *  Oauth2Credentials
  *  _Others TBD_
* Add a `CredentialsContainer` to `org.gradle.api.internal.project.AbstractProject` similar to `org.gradle.api.artifacts.ConfigurationContainer`
* Provide an interface `Secured` which components requiring Credentials must implement. This interface should contain a method which identifies the credential types accepted by the implementing
component in order to avoid mis-configuration.
* Provide a way for build authors and external plugins to add custom credential implementations
  * Credential classes defined in:
    * buildSrc
    * external plugins
    * gradle build files

* Support configuring components with multiple `Credentials` instances - including multiple instances of the same credential type.
* Enhance the existing `repositories` DLS and infrastructure to support credentials from the `CredentialsContainer`
* `org.gradle.api.artifacts.repositories.AuthenticationSupported` must remain backward compatible to support the existing DSL for configuring repository credentials.
* Refactor the AWS S3 repository transport to use this proposed credentials container.


# User visible changes
The following snippet is the proposed DSL structure for adding credential types to the credentials container:  
```groovy
credentials {
    awsKey(AwsKeyCredentials) {
        accessKey = 'xxx'
        secretKey = 'xxx'
    }
    awsIm(AwsIMCredentials) //Purposely empty

    ssh(PublicKeyCredentials) {
        ....
    }
}
```
* The credentials type/class is mandatory.

Anything which uses credentials (implements `Secured`) can be configured as follows:
```groovy
repositories {
    maven {
        url "s3://repo.mycompany.com:22/maven2"
        credentials([awsKey, awsIm]) //Multiple
    }

    maven {
        url "ssh://repo.mycompany.com:22/maven2"
        credentials(ssh)
    }
}
```
# Error Handling
* Early detection of unsupported credentials: if components implementing the 'Secured' interface are required to identify which credential types they accept, bad configurations can be detected early.
* The existing repository components should be ehhanced with some smarts to detect bad configuration, i.e. http backed repositories cannot be configured with `AwsKeyCredentials`


