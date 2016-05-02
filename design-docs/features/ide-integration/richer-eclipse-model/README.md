# Improving the Gradle Eclipse Model

## Motivation

Following our believe that the build should be the single source of truth, Gradle should be
able to express the full range of configuration options that Eclipse offers. Furthermore,
this information should be exposed via the Tooling API, so that Buildship can consume it,
bringing it on par with what users can do when calling `gradle eclipse`.

## Current state and areas for improvement

### General Eclipse projects

Every Eclipse project has two important configuration areas: The .project file,
which contains the fundamental configuration of the project and the .settings folder,
which contains simple property files that plugins can use to store information.

Gradle's model of the .project file is almost complete, only missing resource filters.
The model closely matches the structure of the .project file and users can modify
the model directly, so that changes are reflected both when calling `gradle eclipse`
as well as when using the Tooling API/Buildship.

Gradle has no model of the .settings folder. There is a specific model
for some options that can be set in the org.eclipse.jdt.core.prefs, but no API that
plugin authors could use to write their own settings. There is also no API to ask
for the contents of the .settings folder via the Tooling API. This alone would greatly
enhance the capabilities of Buildship, as most Eclipse plugins put their configuration
in settings files.

### Java Projects

Java projects add the .classpath file on top of that, which consists of a list of
classpath entries like external jars, source folders and library containers.

Gradle's classpath model is very limited. It provides the basic elements like source folders,
project and external dependencies, but can not be enriched with classpath attributes,
output locations, javadoc locations etc. There is no model-based hook for users to customize the classpath.
Instead, there is only an XML-based hook to postprocess the .classpath file.
This breaks a lot of valid use cases, especially in Buildship, where XML-postprocessing hooks are not available.

Furthermore, the Tooling API is missing the list of classpath containers that were added
to the project. As a result, for instance Eclipse Plugin projects are not supported at the moment.

Some of these problems can be worked around by manually modifying the .classpath and checking it
into version control. But this violates our central concept of the build as the single source
of truth. It also means a lot of repetitive manual work for the user.

Specifically, supporting classpath attributes would open up a host of other possibilities.
The proposed "multiple classpath" feature in Eclipse will be based on marking each library
with an attribute that contains the list of source folders that can see that library.

Providing the correct runtime classpath for launch configurations could also be done
with a classpath attribute which Gradle core produces and Buildship consumes.

The XML-postprocessing hooks are problematic in themselves, as users can easily
get into situations where they accidentally create duplicate classpath entries.
This is caused by the fact that Gradle does not mark entries it created, so it cannot
automatically remove them on the next merge operation. If we had a model-based hook
instead and if our model was complete, we could overwrite the files instead of merging them.

### WTP Projects

WTP adds the .component.xml and .facet.core.xml files. Gradle's WTP models only
provide XML-based customization hooks and are not exposed via the Tooling API
at the moment. As a result, customizing WTP projects is harder than it should be
and Buildship cannot support WTP at all.

Since WTP projects declare their external dependencies in the .classpath file,
correctly marking those dependencies as deployed/non-deployed (with a classpath attribute)
is crucial for correct deployment. This is another case where first-class support
for classpath attributes in Gradle Core would transparently fix an issue in Buildship.

## Stories

TBD
