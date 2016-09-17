# The Gradle Wrapper

## The Wrapper downloads Gradle distributions from an authenticated HTTP server

To allow developers working in a restricted/controlled environment to host Gradle distributions, eventually custom ones,
the Gradle Wrapper should support authenticating distributions download requests using HTTP Basic Authentication.   

This should be doable in two different ways: using system properties or embedded in `distributionUrl`.

Using system properties:

    gradle.wrapperUser=username
    gradle.wrapperPassword=password

Embedded in `distributionUrl`:

    distributionUrl=https://username:password@somehost/path/to/gradle-distribution.zip

System properties should take precedence over credentials embedded in `distributionUrl`.

### Implementation notes

No external library should be added to the Wrapper.

The implementation will need a Base64 encoder.
The JVM comes with a public Base64 encoder starting with Java 6 (`DatatypeConverter` from JAXB).
This Base64 encoder is unavailable without adding a module in Java 9 Jigsaw.
Starting with Java 8 the implementation should then use `java.util.Base64`.

### Test cases

- Wrapper can download distributions from an authenticated HTTP server using user credentials from the distribution URL
- Wrapper can download distributions from an authenticated HTTP server using user credentials from the user gradle.properties file
- Wrapper can download distributions from an authenticated HTTP server through a proxy
- Wrapper issues a security warning when using HTTP Basic Auth over an insecure connection
- Wrapper does not issue a security warning when using HTTP Basic Auth over a secure connection
- Credentials are not leaked into output
- Credentials are not leaked in exceptional messages
- Produce a reasonable error message on JVMs that do not include a public Base64 encoder if trying to use HTTP Basic Authentication (Java < 6)
