The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!-- 
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->
[Kyle Cackett](https://github.com/kyle-cackett),
[Roberto Perez Alcolea](https://github.com/rpalcolea),
[Daniel Thomas](https://github.com/DanielThomas),
[jeffalder](https://github.com/jeffalder),
[FICHET Philippe](https://github.com/philippefichet),
[Johnny Lim](https://github.com/izeye),
[Bow Archer](https://github.com/decoded4620),
and [Sam De Block](https://github.com/SamDeBlock).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@. 

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. --> 

<!-- 
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

## Show location of Java crash log

Sometimes, the Gradle daemon crashes due to some bugs in Java, Gradle or some third party plugin.
When Java crashes, it writes a [fatal error log](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/felog.html) to the current working directory.
Since it may be hard to find out what the working directory of the daemon process is, Gradle now prints the location of the crash log to the console.
For example, the following build crashed, and creates a crash log at `/home/user/project/hs_err_pid11783.log`: 

```
> ./gradlew assemble

Starting a Gradle Daemon (subsequent builds will be faster)
The message received from the daemon indicates that the daemon has disappeared.
Build request sent: Build{id=44fa8d49-d89a-41ca-a265-56e676ed40e6, currentDir=/home/user/project}
Attempting to read last messages from the daemon log...
Daemon pid: 11783
  log file: /home/user/.gradle/daemon/6.3/daemon-11783.out.log
----- Last  20 lines from daemon log file - daemon-11783.out.log -----
2020-03-03T16:44:13.580+0100 [DEBUG] [org.gradle.cache.internal.DefaultFileLockManager] Waiting to acquire exclusive lock on daemon addresses registry.
...
----- End of the daemon log -----

JVM crash log found: file:///home/user/project/hs_err_pid11783.log

FAILURE: Build failed with an exception.

* What went wrong:
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)

* Try:
Run with --info or --debug option to get more log output. Run with --scan to get full insights.
```

## Improvements for plugin authors

### Experimental improvements for reliable build configuration 

TBD - this section needs some edits to clarify what this is and why it is useful; also move some of this to the user manual

A common source of problems when writing Gradle builds or plugins is the brittleness that happens due to the ordering of plugin application, particular
when the order changes.
The so called 'lazy types'(link) help address these problems by allowing plugins and build scripts to connect calculated values together. For example, a plugin
can connect the output of a compile task as an input of a Jar task, before the final output location or any other details for the compile task are known.
Currently, these types can allow 'unsafe reads' to happen prior to task execution, for example where a plugin may query and se a property value then another plugin
changes the value.

This release adds a `disallowUnsafeRead()` method to `Property`(link) and `ConfigurableFileCollection`(link). This method switches the instance to 'strict reads' mode.
In this mode, a property value cannot be queried during the configuration of the project it belongs to. The property can be configured and connected to other properties in the usual way.
When the property value is queried it is first made read-only. This means that a property instance will only ever have a single value, while allowing all plugins to contribute to the value.

This behaviour is intended to become the default in a future major version of Gradle. This method allows plugin authors to experiment with this behaviour. It does, however, impact the
users of the plugin and so should be used carefully.

For more details see (user manual link)

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines. 
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
