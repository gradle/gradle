# Signing key for Gradle artifacts

Gradle aims to sign every artifact it produces, whether the artifact is published to a repository or shipped through its release distribution channel.

This document describes the **current** key used to sign releases, and preserves earlier keys so older releases remain verifiable.

The same information is also available from the [Gradle website](https://gradle.org/keys/) and the current key can be retrieved from [public key servers](https://keys.openpgp.org/search?q=maven-publishing%40gradle.com).

## Current key

- **Key ID**: `2887F479B0B9771A`
- **Fingerprint**: `EA96F38569C044AAEF7FCF732887F479B0B9771A`
- **Created**: 2026-08-14
- **Used for**: Publications to artifact repositories under the `org.gradle.*` groups, like [Maven Central](https://repo1.maven.org/maven2/org/gradle/) or the [Plugin Portal](https://plugins.gradle.org/u/prod-plugin-portal-publishing) and [Gradle distribution releases](https://services.gradle.org/distributions/).

Signing is done by a dedicated subkey rather than by the primary key:

| Role            | Key ID             | Fingerprint                                |
|-----------------|--------------------|--------------------------------------------|
| Primary (certify) | `2887F479B0B9771A` | `EA96F38569C044AAEF7FCF732887F479B0B9771A` |
| Signing subkey  | `51FBF517CE6D6B80` | `F3FF33E96F18AA62DD580F9651FBF517CE6D6B80` |

Importing the key gives you both, so verification works without naming the subkey explicitly.

Note that `gpg --verify` identifies the key that actually made the signature, hence the subkey `51FBF517CE6D6B80`.

The `Good signature from "Gradle Inc. <maven-publishing@gradle.com>"` line, and the `Primary key fingerprint` line, when shown, still refer to the primary key.

The full ASCII-armored block is at the bottom of this document, under [Public key blocks](#public-key-blocks).

## Previous keys

The keys below were used to sign earlier Gradle artifacts. To verify a release that predates the current key, import the appropriate previous key from [Previous key blocks](#previous-key-blocks) at the bottom of this document.

| Key ID              | Fingerprint                                | Active from | Status                              |
|---------------------|--------------------------------------------|-------------|-------------------------------------|
| `E2F38302C8075E3D`  | `1BD97A6A154E7810EE0BC832E2F38302C8075E3D` | 2022-12-29  | Revoked 2026-08-08 (key superseded) |

A revocation certificate was published for `E2F38302C8075E3D` on 2026-08-08, with the revocation reason *key is superseded*.
Signatures made on artifacts while this key was active remain valid: the revocation records indicate that the key is retired from service, not that its private material was compromised.

The preserved block below includes the revocation, so importing it tells your GPG installation that the key is retired.

You can also fetch the previous key, with its revocation, from `keyserver.ubuntu.com`:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys 1BD97A6A154E7810EE0BC832E2F38302C8075E3D
```

## Verification instructions

### Importing a key

You can import a Gradle signing key into your GPG keyring in one of two ways.

**From an ascii-armored block in this document.** Copy the relevant block into a file called `gradle_pubkey.asc`, then run:

```bash
gpg --import gradle_pubkey.asc
```

**From a key server.** Fetch by fingerprint. For the current key:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys EA96F38569C044AAEF7FCF732887F479B0B9771A
```

> **Note**: Use `keyserver.ubuntu.com` rather than `keys.openpgp.org` for previous keys.
> `keys.openpgp.org` associates an email address with only one key at a time, so verifying `maven-publishing@gradle.com` for the current key removed that address from the previous key.
> The previous key is still served there, but without its user ID, and GPG declines such an import with `no user ID`.

### Verifying signatures

Once you've downloaded a Gradle JAR or distribution and its corresponding `.asc` signature file, verify authenticity against the imported key:

```bash
gpg --verify <artifact>.asc <artifact>
```

> **Note**: As of 2026-08-19, no publicly released Gradle artifacts are signed with the current key (`2887F479B0B9771A`); releases through Gradle 9.7.0 were signed with the previous key (`E2F38302C8075E3D`).
> The worked example below therefore uses the previous key.
> Once the current key is in use, run the same commands after importing it instead.

The following worked example uses the previous key: download a distribution and its signature, import the previous key from the [preserved block](#previous-key-blocks) in this document, then verify:

```bash
curl -O https://services.gradle.org/distributions/gradle-9.7.0-bin.zip
curl -O https://services.gradle.org/distributions/gradle-9.7.0-bin.zip.asc

gpg --verify gradle-9.7.0-bin.zip.asc gradle-9.7.0-bin.zip
```

Because the previous key is revoked, GPG reports a good signature *and* warns that the key is retired:

```
gpg: Signature made Thu  6 Aug 16:14:52 2026 CEST
gpg:                using RSA key E2F38302C8075E3D
gpg: Good signature from "Gradle Inc. <maven-publishing@gradle.com>" [unknown]
gpg: WARNING: This key has been revoked by its owner!
gpg:          This could mean that the signature is forged.
gpg: reason for revocation: Key is superseded
```

`Good signature` is the result that matters, and the command exits with `0`.
The `could mean that the signature is forged` line is text GPG prints for any revoked key regardless of why it was revoked; here, the reason is *key is superseded*, so the signature is intact, and the warning only reflects that the key is no longer in service.

### Trusting the key in dependency verification

If you use Gradle's [dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html) to verify Gradle's own artifacts, a `<trusted-key>` entry must name the key that *made the signature*.
For the current key, name the signing subkey, not the primary key:

```xml
<trusted-key id="F3FF33E96F18AA62DD580F9651FBF517CE6D6B80" group="org.gradle"/>
```

Gradle does not currently resolve a subkey to its primary key, so naming the primary fingerprint instead fails with `this key is not in your trusted key list`.
Allowing a primary key to cover its subkeys is tracked in [gradle/gradle#37607](https://github.com/gradle/gradle/issues/37607).

> **Note**: The previous key signed with its primary key, so a `<trusted-key>` entry naming `1BD97A6A154E7810EE0BC832E2F38302C8075E3D` still matches for those releases.
> Configurations that trust the previous key by fingerprint will need the current key's subkey fingerprint added once artifacts signed with it are published.

### Trusting a key locally

If you see a warning like `gpg: WARNING: This key is not certified with a trusted signature!`, you can locally sign the imported Gradle key to suppress it. This tells your GPG installation that you trust the key.

```bash
gpg --sign-key <fingerprint>
```

Replace `<fingerprint>` with the fingerprint of the key you imported, either the current key or a previous one.

## Public key blocks

### Current key

```
-----BEGIN PGP PUBLIC KEY BLOCK-----

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
=V8Ww
-----END PGP PUBLIC KEY BLOCK-----
```

### Previous key blocks

<details>
<summary>Previous key: <code>E2F38302C8075E3D</code></summary>

```
-----BEGIN PGP PUBLIC KEY BLOCK-----

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
tw==
=bjra
-----END PGP PUBLIC KEY BLOCK-----
```

</details>
