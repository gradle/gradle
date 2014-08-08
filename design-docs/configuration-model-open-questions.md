## Decoration of model objects

Given I declare a model object of some type T:

- Where and how do we present a proxy for that object to rules?
- How can I declare an implementation type (if supported) or internal and public contract type(s) to present the object as?
- How do I do this from the DSL? A plugin?

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
