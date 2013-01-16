Some issues with SourceSet:

* Has a runtimeClasspath. This doesn't make sense when we're not targeting the JVM. C++ and
javascript source do have runtime dependencies, but these dependencies do not end up as a
classpath. For both C++ and javascript, we're also interested in building different variants,
where each variant can potentially have a different set of resolved dependencies. Supporting
variants also makes a lot of sense in the JVM world too (groovy-1.7 vs groovy-1.8, for example).

* SourceSetOutput effectively represents a set of JVM byte code. This is the same as the issue above.
Modelling the compiled source as byte code doesn't make sense when we're not targeting the JVM. Also,
each variant of the same source will generally end up with different compiled output.

* Has a compileClasspath. As above. Also assumes that we're actually compiling something. And that
all languages share the same compile classpath.

* Has a (possibly empty) set of Java source to be compiled and included in the runtime classpath.
This doesn't make any sense if there's no Java source  in the project.

* Has a set of resources to be included in the runtime classpath. This doesn't make any sense if
we're not targeting the JVM.

There are also some language specific issues:

* Java should have a source language level, and an annotation-processor classpath.

* Groovy should have a source language level, and separate compile, language-runtime,
compile-implementation, and transformer class paths. Scala should have something similar.

* The ANTLR plugin assumes we're generating a parser to run on the JVM. The tooling may run on the
JVM, but the generated source may not.

* For each language, we should distinguish between generated and non-generated source.

I think I'd like to turn the current SourceSet/SourceSetOutput/GroovySourceSet/ScalaSourceSet/
CppSourceSet into something like this:

* Interfaces that represent language-specific set of source, and specifies output-independent
meta-data about the source: things like source directories and include/exclude patterns,
compile and runtime dependencies, language level, and so on. So, we'd have a JavaSourceSet,
GroovySourceSet, ScalaSourceSet, CppSourceSet, JavaScriptSourceSet and so on.

* An interface that represents a composite set of source files. This would be used to group
language-specific sets to describe their purpose. This type would be used for 'sourceSets.main'
and 'sourceSets.test'.

* Interfaces that represent runtime-specific set of executable files. These would be used to
represent the output of the source sets, one per variant that we can build. For JVM languages,
we'd use something that represents a tree of class and resource files. For native languages, we'd
use something that represents a set of object files. For javascript, we'd use a JavaSourceSet.

* All of the above would extend Buildable. This better models generated source (but doesn't quite
solve the problem on its own), allows a separate processing pipeline to be assembled to build the
output for each variant, and allows us to handle executable files that we don't build, but need to
bundle or publish.

* There would be some way to navigate between the outputs of a source set and the source set itself.
Not sure exactly how that should look. Each language source set ends up built into one or more
output. Each runtime output is built from one or more language source sets. Maybe the association
is only by name.

My thought is that we want to model the transformed output as concrete things, rather than abstract
'transformed source' things. That is, we should model things like jvm libraries, javascript libraries,
reports, web applications, native binaries, and so on, each with type specific meta-data and configuration.

Each of these things should have a name, and a type. They would be Buildable, which gives us a place
to define the processing pipeline to build the thing. You should be able to navigate to the inputs of
 each thing. I'd say a source set would be a kind of this thing, too.

This way, we have a graph of buildable things, and we know the inputs and outputs of each thing in the
graph, plus the associated tasks to execute to transform the inputs things into the output thing.
Sounds kinda familiar :)

This gives us a bunch of potential goodness:

* We introduce some concrete models for the things we actually build. This in turn leads to lots
of goodness.
* We can short-circuit the execution and configuration of the tasks that build a thing whose inputs
and outputs are up-to-date.
* We can log progress in terms of these things, rather than at the task level (eg. just log 'myBinary
UP-TO-DATE' instead of each of the tasks that build myBinary).
* We can define the inputs and output of a project in terms of these things. Which means we can
short-circuit the configuration of a project whose outgoing things are up-to-date wrt their input
things. Or we can substitute in things pre-built elsewhere, and short-circuit configuring the target
project.
* Incoming dependencies can be expressed in terms of these concrete things, instead of abstract 'modules'
and 'configurations'. This, I think, helps with understandability.
* Works nicely with the 'keep this thing continuously up-to-date' feature we were discussing a few days ago.

So, the Gradle model becomes a graph of nodes that represent the things you want to build or use, with
edges representing dependencies on other things. Things can either be built locally, or can come from
somewhere else.
