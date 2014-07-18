Gradle's CheckStyle, PMD, and FindBugs plugins allow the respective tools to be configured via (their own) configuration files. In an organization, it can be desirable to share the same configuration files between many builds. One potential approach for sharing configuration files is to package them as Jars, which are then shared via a binary repository. Another approach is to share configuration files as HTTP resources. This spec is about the binary repository approach, which benefits from all the advantages of Gradle's dependency resolution and caching features.

For PMD, sharing configuration files via a binary repository is already possible. For example:

    configurations {
      pmdRules
    }
    dependencies {
      pmdRules "my.org:my-pmd-rules:1.0"
    }
    tasks.withType(Pmd) {
      pmdClasspath += configurations.pmdRules
      ruleSets = ["my_pmd_ruleset.xml"]
    }

This leaves Checkstyle and FindBugs.

# Use cases

Enforce organization code standards by sharing the same Checkstyle, PMD, and FindBugs configuration files between many Gradle builds. 

# Implementation plan

## Allow Checkstyle configuration files to be loaded from (Checkstyle) class path

### User visible changes

Introduce a `String configResource` property on the `Checkstyle` task. Only one of `configResource` and `configFile` can be set.

### Implementation

Pass on the value of the `configResource` property to the `config` property of the Ant task.

### Test coverage

* Package a ruleset file as a Jar
* Add the Jar to `Checkstyle#checkstyleClasspath`
* Set `Checkstyle#configResourceName` appropriately
* Execute the Checkstyle task and verify that the ruleset takes effect

## Allow FindBugs bug filter include/exclude files to be loaded from (Findbugs) class path

### User visible changes

Introduce `String includeFilterResource` and `excludeFilterResource` properties on the `FindBugs` tasks. Only one of `includeFilter` and `includeFilterResource` can be set. Only one of `excludeFilter` and `excludeFilterResource` can be set.

### Implementation

If configured, extract include/exclude filter resources from `pluginClasspath` (or `findbugsClasspath`?), write them to a file, and pass the files to FindBugs.

### Test coverage

* Package bug filter include/exclude files as a Jar
* Add the Jar to the FindBugs (plugin) class path
* Set `FindBugs#includeFilterResource` and `FindBugs#excludeFilterResource` appropriately
* Execute the FindBugs task and verify that the bug filters takes effect

# Open issues

* Instead of introducing new properties, the existing properties could be generalized to accept either a class path resource name or a file location. However, this would constitute a breaking change, as the properties' type would have to change from `File` to `Object`. Another option would be to introduce new generalized properties while deprecating the existing ones.
* FindBugs: Resource files could be extracted using `project.copy`/`zipTree`, or accessed using a class loader. Extraction could happen in the Gradle process or the FindBugs worker process.
