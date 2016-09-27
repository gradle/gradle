# Motivation

Eclipse provides a resource filter API which allows users to exclude parts of the physical file tree
from the virtual file tree in the IDE. Excluding resources can be a huge performance boost. 
In Node.js projects for instance, the node-modules folder contains thousands of files that the user will never manually touch,
but which slow down the IDE refresh.

# Proposed Change

## User-Facing DSL

Gradle's EclipseProject DSL should allow the definition of resource filters. The DSL should closely
mimic the `Resource Filters` UI in Eclipse. The following features should be supported:

- a list of resource filters
- the target of the filter (files/folders/both)
- whether this filter includes or excludes the matched resources
- a root matcher for each filter
- the built-in matchers 'attributes', 'and', 'or' and 'not' as well as a generic 'matcher'
- matchers can contain other matchers (e.g. 'and', 'or', 'not', but there might be others not shipped with Eclipse by default)
- the attributes matcher supports setting the attribute, operator, value.
- if the attribute is one of the string-valued ones ('name', 'location' and 'projectRelativePath'), then the regex and ignoreCase flags are also allowed
- the allowed operators depend on the type of the attribute
    - 'matches' for string attributes ('name', 'location' and 'projectRelativePath')
    - 'equals', 'before', 'after' and 'within' for date attributes ('dateCreated', 'lastModified')
    - 'equals', 'smallerThan', 'largerThan' for numerical attributes ('fileLength')
    - 'equals' for boolean attributes ('isSymlink', 'isReadOnly')

Here's an example of a simple resource filter that excludes the '.gradle' directory:

```
eclipse.project.resourceFilters {
  filter {
    appliesTo = FILES_AND_FOLDERS
    type = EXCLUDE_ALL
    recursive = false
    attributes {
      attribute = 'name'
      operator = 'matches'
      value = '.gradle'
    }
  }
}
```

which will produce the following XML in the .project file:

```
<filter>
    <!-- auto-generated -->
    <id>1472802786520</id>
    <!-- always empty -->
    <name></name>
    <!-- EXCLUDE_ALL | FILES_AND_FOLDERS -->
    <type>10</type>
    <matcher>
        <!-- the ID of what the UI calls the 'attributes' filter -->
        <id>org.eclipse.ui.ide.multiFilter</id> 
        <!-- version-attribute-operator-ignoreCase-regex-value -->
        <arguments>1.0-name-matches-false-false-.gradle</arguments>
    </matcher>
</filter>
```


Here's a more complex example:

```
resourceFilters {
  filter {
    appliesTo = FILES
    type = INCLUDE_ONLY
    recursive = true
    and {
      //writable
      attributes {
        attribute = 'isReadOnly'
        operator = 'equals'
        value = 'false'
      }
      or {
        //last modified within the last 3h
        attributes {
          attribute = 'lastModified'
          operator = 'within'
          value = '10800'
        }
        //somewhere below the 'src' folder
        attributes {
          attribute = 'projectRelativePath'
          operator = 'matches'
          value = "src/*"
        }
      }
    }
  }
}
```

Which produces the follwing XML:

```
<filter>
    <id>1472804891278</id>
    <name></name>
    <type>5</type>
    <matcher>
        <id>org.eclipse.ui.ide.andFilterMatcher</id>
        <arguments>
            <matcher>
                <id>org.eclipse.ui.ide.multiFilter</id>
                <arguments>1.0-isReadOnly-equals-false-false-false</arguments>
            </matcher>
            <matcher>
                <id>org.eclipse.ui.ide.orFilterMatcher</id>
                <arguments>
                    <matcher>
                        <id>org.eclipse.ui.ide.multiFilter</id>
                        <arguments>1.0-lastModified-within-false-false-10800</arguments>
                    </matcher>
                    <matcher>
                        <id>org.eclipse.ui.ide.multiFilter</id>
                        <arguments>1.0-projectRelativePath-matches-true-false-src/*</arguments>
                    </matcher>
                </arguments>
            </matcher>
        </arguments>
    </matcher>
</filter>
```

Apart from the built-in filters, we should also support custom matchers that are not part of Eclipse's defaults:

```
resourceFilters {
  filter {
    appliesTo = FILES_AND_FOLDERS
    type = EXCLUDE_ALL
    recursive = false
    matcher {
      id = 'org.eclipse.some.custom.matcher'
      // a matcher can either have no argument OR a String-valued argument OR have child matchers 
      arguments = 'foobar'
    }
  }
}
```

In fact, supporting this generic `matcher` method first and adding the convenience methods for the built-in matchers later would be a good
implementation plan.

## Tooling API

The EclipseProject Tooling API model should be enhanced with support for resource filters too. This model can be much simpler than the DSL,
since it is only used to transfer data to Buildship:

```
EclipseProject {
    List<? extends ResourceFilter> getResourceFilters()
}

ResourceFilter {
  ResourceFilterType getType() //INCLUDE_ONLY/EXCLUDE_ALL
  ResourceFilterTarget getTarget() //FILES, FOLDERS, FILES_AND_FOLDERS
  boolean isRecursive()
  ResourceFilterMatcher getMatcher()
}

ResourceFilterMatcher {
    String getId();
    @Nullable
    String getArguments();
    List<? extends ResourceFilterMatcher> getChildren()
}
```

# Test Coverage

## DSL

- a simple attribute filter can be defined in the DSL
- and/or/not matchers can be defined in the DSL
- custom matchers can be defined in the DSL
- for the attributes matcher, the list of supported attributes, operators and flags is restricted to what Eclipse actually supports

## XML generation

- filters can be read from an existing .project file and are translated into the more convenient DSL types (like AttributesMatcher) where applicable
- a unique ID is auto-generated (containing the current epoch timestamp)
- when a filter with the exact same configuration already exists in the .project file, its ID stays unchanged
- each combination of `type` and `appliesTo` in the DSL should generate the correct `type` bitmask in the generated XML (see org.eclipse.core.resources.IFilterMatcherDescriptor)
- each matcher type from the DSL is mapped to its appropriate Eclipse matcher id
- the examples above are generated correctly

## Tooling API

- trying to access resource filters on an old version of Gradle throws an `UnsupportedMethodException`
- resource filters defined in the DSL are present in the tooling model for all future Gradle versions
- if a matcher contains other matchers, the `arguments` String is null

