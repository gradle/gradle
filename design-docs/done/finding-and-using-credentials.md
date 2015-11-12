This specification is a proposal for the introduction of a top-level credentials container and some fundamental changes to
how repository transports and authentication mechanisms are modeled.

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

