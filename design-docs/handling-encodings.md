# Encoding

This spec describes improvements to allow build authors to take control of character encoding in their build.
It applies to tasks which need to read, write and transform text files, as well as tasks which create archives (zip
files, tar files, etc.).

Any time we need to generate a text file, or to read, transform and write the contents of a text file, we need to know
what charset(s) to use.
Some tasks might need to use a charset to write that is different from the charset used to read.
For the most frequent case, i.e. copying and filtering files, it's a reasonable assumption to expect the developer to
choose the target encoding for the source files, and thus to use a single filtering charset both for reading and
writing.

As a general rule of thumb, the default character set to use is the default platform character set as defined by the
`file.encoding` system property, also accessible via `Charset.defaultCharset()`.
APIs should provide a way for build authors to control the character set to use.

But, when it comes to encoding text inside a binary format, like in archives metadata, the corresponding binary formats
specifications prevail.

<a name="impl-notes"></a>
## Implementation notes

When it comes to testing character encoding related issues, a good candidate is the `€` symbol which is encoded
differently in `ISO-8859-15` (`0xA4`) than in `UTF-8` (`0x20AC`).

<a name="jar"></a>
## About ZIP/JAR

Gradle makes use of the [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/zip.html) library
to handle the ZIP file format.

The ZIP specification only supports CodePage 437 (basically ascii for visible characters), or UTF-8 if a special bit is
set (*Language encoding flag* or EFS).
See [Section 4.4.4 and appendix D1 & D2](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) of the ZIP
specification.

Before that specification, InfoZIP vendor introduced an extra metadata field to store an UTF-8 encoded version of
filenames alongside the main ones.
Most tools ignore that extra field, Gradle should do the same.

For [JAR, WAR and EAR](http://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html) archives only the UTF-8
character set is supported.
All tools in a JVM dealing with ZIP files, incl. the `jar` command and classpath loading, use the
[`java.util.jar`](https://docs.oracle.com/javase/8/docs/api/java/util/jar/package-summary.html#package.description)
package that makes use of the
[`java.util.zip`](https://docs.oracle.com/javase/8/docs/api/java/util/zip/package-summary.html#package.description)
package that uses UTF-8 by default to both read and write archives metadata.

In Gradle, up to version 2.13 at least, the `Zip`, `Jar`, `War` and `Ear` tasks all have an `encoding` property added
in [Pull Request 499](https://github.com/gradle/gradle/pull/499) which allows specifying a metadata charset, which
defaults to the platform default charset.
The name of this property is confusing, and the default value is not correct,
see [GRADLE-1506](https://issues.gradle.org/browse/GRADLE-1506).
As already said above, JAR, WAR and EAR metadata should always be encoded in UTF-8 instead of the platform
default encoding.

The Apache Commons Compress team has been running interoperability tests, the
[results](https://commons.apache.org/proper/commons-compress/zip.html#Recommendations_for_Interoperability) are of
interest.
Note that since then, starting with Windows 8, the Windows "Compressed Folder" feature recognize the language encoding
flag and thus supports UTF-8.

Producing ZIP archives with metadata encoded using the UTF-8 character set and setting the EFS flag seems to be way to
create interoperable archives as it provides predictable encoding/decoding of filenames.
We should allow it while retaining backward compatibility.

<a name="tar"></a>
## About TAR

Gradle makes use of the [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/tar.html) to handle
the TAR file format.

There are mainly two TAR flavours in the wild: GNU and POSIX.1-2001 (aka. PAX).
Both of theses flavor extend from the 1988 POSIX USTAR specification that uses ASCII only.
See the [File format](https://en.wikipedia.org/wiki/Tar_(computing)#File_format) section of the Tar page on Wikipedia.

The GNU flavour uses default platform encoding for file names both when creating and extracting archives.
This can lead to mojibakes in filenames when extracting on a system with a different default platform encoding.
This is the behaviour of Gradle up to version 2.13 at least.

The PAX flavour, a now 15 years old specification that is widely supported, says that implementations should encode
filenames to UTF-8 when creating archives and decode them to the default platform encoding when extracting archives.
See the [pax Archive Character Set Encoding/Decoding](http://pubs.opengroup.org/onlinepubs/009695399/utilities/pax.html#tag_04_100_18_02)
section of the specification.

All TAR commands/tools out there should support creation and at least extraction of theses two flavours.
The format (GNU vs. POSIX/PAX) of TAR files metadata is automatically detected on read by tools, including the Apache
Commons Compress library used in Gradle.

The GNU manual says the following under [the POSIX section](https://www.gnu.org/software/tar/manual/html_node/Formats.html#SEC132):

> This archive format will be the default format for future versions of GNU tar.

Producing PAX TAR archives with metadata encoded using the UTF-8 character set seems to be way to create interoperable
archives as it provides predictable encoding/decoding of filenames.
We should allow it while retaining backward compatibility.

## Open Questions

Currently scripts are compiled using the current locale. Obviously this is an issue if the build script contains system-dependent characters and is used on a different platform. What should we do in that case?

[//]: # (Any?)

## Stories

* [`CopySpec` reading and writing text files when filtering](#copyspec-filtering)
* [`ZipFileTree` and `TarFileTree` reading ZIPs and TARs metadata](#zip-tar-read)
* [`Zip`, `Jar`, `War`, `Ear` tasks writing archives metadata](#zip-write)
* [`Tar` task writing TARs metadata](#tar-write)
* [`Jar`, `War`, `Ear` tasks writing manifests](#jar-manifest)

There may be other use cases involving character encoding than the ones mentioned in this document.
Please add them to this inventory.

<a name="copyspec-filtering"></a>
### Story: Control the character set used when reading and writing filtered files content

See [GRADLE-1267](https://issues.gradle.org/browse/GRADLE-1267) and [PR#520](https://github.com/gradle/gradle/pull/520).

CopySpec should get a new `filteringCharset` property of type `String` which is the name of the `Charset` which will
be used for encoding and decoding the contents of a file on either side of passing the contents to the closure(s)
specified by the various `filter` methods of `CopySpec`.

The default value of `filteringCharset` should be the name of the platform default charset.
That will maintain backward compatibility, since up to Gradle 2.13 at least, the platform default charset is used.

By the way, when not filtering the files, the `Copy` task treats them as binary files, and thus does no
encoding/decoding at all.

The `CopySpec` interface documentation should document the `filteringCharset` property and specify the default value if
the property is not set.
Since the `Copy` task inherits from `CopySpec`, that should document the task automatically.

#### Tests

- The default charset is honored when `filteringCharset` is not set.
- When `filteringCharset` is set to something other than the default platform charset, it is honored.
- When a specified charset is not a valid charset name, an exception is thrown immediately, with a clear error message.

<a name="zip-tar-read"></a>
### Story: Control the character set used for metadata when reading ZIP and TAR archives

When reading from ZIP files using `ZipFileTree` or from TAR files using `TarFileTree`, there should be a
`metadataCharset` property which determines the `Charset` which will be used to read the file entries and header data
from the ZIP and TAR archives, respectively.

Reading a ZIP:

    task readZip(type: Copy) {
        from zipTree('someFile.zip') {
            metadataCharset = 'UTF-8'
            // ...
        }
        // ...
    }

Reading a TAR:

    task readTar(type: Copy) {
        from tarTree('someFile.tar') {
            metadataCharset = 'UTF-8'
            // ...
        }
        // ...
    }

In order to keep backward compatibility the `metadataCharset` default value for both `ZipFileTree` and `TarFileTree`
should be the name of the platform default character set.

#### Tests

- The default charset is honored when `metadataCharset` is not set.
- When `metadataCharset` is set to something other than the default platform charset, it is honored.
- When a specified charset is not a valid charset name, an exception is thrown immediately, with a clear error message.

<a name="zip-write"></a>
### Story: Control the character set used for metadata when creating ZIP/JAR/WAR/EAR archives

When creating ZIP archives with the `Zip` task, there should be a `metadataCharset` property which determines the
`Charset` which will be used to create the file entries and header data in the ZIP archive.

In order to keep backward compatibility the `metadataCharset` default value for the `Zip` task should be the name of the
platform default character set.

The `metadataCharset` property should be marked as an input to the task using `@Input`.

The existing `encoding` property of the `Zip` task should be deprecated and removed in Gradle 3.0.
In the meantime, its getter and setter should delegate to the new `metadataCharset` property.

When `metadataCharset` is UTF-8 the `Zip` task should properly set the *Language encoding flag* or EFS.

When creating WAR, EAR, and JAR files which all extend from the `Zip` task, we should set default value of
`metadataCharset`to `UTF-8` as that is the expectation of the JVM.

This is a potential breaking change.
To get the old behaviour the `metadataCharset` property could be set as follows:

    task oldBehaviour(type:Jar) {
        metadataCharset = Charset.defaultCharset().name()
    }

The `Zip` task should document the `metadataCharset` property and specify the default value if the property is not set.

The `Jar`, `War` and `Ear` tasks should document the `metadataCharset` property, specify the default value of `UTF-8`
and warn about using something else.

#### Tests

- See existing tests in `JarIntegrationTest` for JAR/WAR/EAR metadata encoding

[//]: # (TODO add tests)

<a name="tar-write"></a>
### Story: Control the character set used for metadata when creating TAR archives

When creating TAR archives with the `Tar` task, there should be:

- a `tarFormat` property which determines which TAR flavour to use, `gnu` or `posix` ;
- a `metadataCharset` property which determines the `Charset` which will be used to create the file entries and header
  data in the TAR archive.

In order to keep backward compatibility the `tarFormat` default value should be `gnu` and the `metadataCharset` default
value should be the name of the platform default character set when `tarFormat` is `gnu`, UTF-8 if it is `posix`.

Having theses two properties allow build authors to have full control on created TAR archives while retaining backward
compatibility.

Both of theses properties should be marked as an input to the task using `@Input`.

Create GNU TAR using the platform default character set, the unchanged default behaviour:

    task gnuTar(type: Tar) {
        // tarFormat = 'gnu'
        // metadataCharset = Charset.defaultCharset().name()
        // ...
    }

Create GNU TAR using ASCII for metadata encoding:

    task gnuAsciiTar(type: Tar) {
        // tarFormat = 'gnu'
        metadataCharset = 'ASCII'
        // ...
    }

Create POSIX/PAX TAR using UTF-8 for metadata encoding, that is the most interoperable:

    task posixPaxTar(type: Tar) {
        tarFormat = 'posix'
        // metadataCharset = 'UTF-8'
        // ...
    }

Create POSIX/PAX TAR using ISO-8859 for metadata encoding:

    task posixPaxIso8859Tar(type: Tar) {
        tarFormat = 'posix'
        metadataCharset = 'ISO-8859'
        // ...
    }

#### Tests

[//]: # (TODO add tests)

<a name="jar-manifest"></a>
### Story: JAR/WAR/EAR manifests are always encoded using UTF-8

Gradle use the platform default character set to encode the manifest content.
Manifests must be encoded using UTF-8.
This is a bug and it should be fixed, see [GRADLE-3374](https://issues.gradle.org/browse/GRADLE-3374).

Manifests can be merged and so Gradle read manifests from files.
The platform default charset is also used to decode the merged manifests content.

By default, all manifests should be encoded using UTF-8, all merged manifests should be decoded using UTF-8:

    task jar(type: Jar) {
        manifest {
            from('src/config/javabasemanifest.txt')
        }
    }

Previous behaviour:

    task jar(type: Jar) {
        manifest {
            contentCharset = Charset.defaultCharset().name()
            from('src/config/javabasemanifest.txt') {
                contentCharset = Charset.defaultCharset().name()
            }
        }
    }

#### Tests

- by default, whatever the platform default character set, JAR/WAR/EAR manifests are encoded using UTF-8
- by default, merged manifests are decoded using UTF-8
- build author can control which character set is used for both encoding/decoding JAR manifests
- manifest attributes with split multi-bytes characters are read/merged/written correctly
- See existing tests in `JarIntegrationTest` for manifest content encoding
