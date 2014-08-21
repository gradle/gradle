## Decoration of model objects

Given I declare a model object of some type T:

- Where and how do we present a proxy for that object to rules?
- How can I declare an implementation type (if supported) or internal and public contract type(s) to present the object as?
- How do I do this from the DSL? A plugin?
- Should share approach and implementation with whatever proxying the tooling API does

## Model object relationships

- How do I define a graph of model objects?
- How can different rules contribute to the graph?
- Given that I ask for an object, how do I declare that I want the graph (or sub-graph) that is reachable from that
objects, vs (say) just the properties of that object? Same question for inputs and outputs.
- What does the DSL look like?

## Model identity

Dealing with model objects that appear in multiple locations in the namespace, or can be reached in various
different criteria (eg path or type) from the DSL or a plugin.

## Model views

- How do I define views for a given object or class of objects?
- How can I extend an object to add more stuff to it?

## Mock ups

### Some notes - Adam

A rule:
- Declares which objects are the input of rule.
- Declares which objects are the output of the rule.
- Declares the view it requires of each object.
- Declares some function to execute to produce outputs from some given input.
- The function is invoked once for each set of input objects that meet the declared criteria.

Considerations:
- An input may also be an output, for example when the rule mutates an object.
- There may be relationships between the inputs and outputs, for example to configure a native test suite I want to
use the component under test as input to determine the variants to build for the test suite.
- A DSL or API should declare as much as possible statically.
- Should take advantage of static information when selecting objects.
- A 'function' here may be user provided logic or it might be some implicit behaviour provided by the runtime. In other words,
a function is some work that is performed, for which we have varying degrees of knowledge of what it does: is it 'fast' or 'slow'?
is it deterministic? do we know all its inputs?

Types of rule actions:
- Declare some top level object (in some scope).
- Declare some object as the value of a single-valued property of some other object.
- Declare some object in a multi-valued property of some other object.
- Apply conventions to an object before it is configured
- Configure some object.
- Apply conventions to an object after it is configured.
- Validate some object before it is used as an input.
- Extend an object to attach properties.
- Specialize an object.
- Declare meta-data about some type.
- Declare further rules.

We can consider the outer scope as simply a multi-valued property on some root object, which means that declaring a top level object can
be treated the same way as attaching some object to some container. We can also consider the definition of rules as declaring rules in some
container object.
Given this, each of these rules above mutate zero or more of its inputs.
This means that there is no need to express criteria to select an output from the namespace. This is implicit
from the criteria for the inputs. This has implications for the DSL.

A rule can be considered as a template, which is some criteria to select input objects and a function. From this template zero or more actions are created,
one for each set of matching input objects. Each action is a function bound to a particular set of inputs.

Under this, a `Task` is an action.

A rule generally can be transformed into a rule with broader criteria whose action defines a further rule with the specific criteria.
This has implications for the DSL, as some criteria can be expressed statically in method signatures and some additional criteria can be expressed as code.

### Add some stuff here
