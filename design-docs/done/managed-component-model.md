
###  `components.«component».source.«sourceSet»` is addressable/visible in rule space

#### Implementation

* Change implementation of `components` node to eagerly create a `source` child node for each element node, with the object returned by `componentSpec.sources`
* `components.«name».source` is projected using the unmanaged projection (i.e. it is opaque)
* Use `.all()` hook of component's source set container to create the child nodes of the `source` node as unmanaged node, based on type of the source set given to the origin create() method
* Change all removal type operations of `«component».source` to throw `UnsupportedOperationException`

#### Test Coverage

- ~~Can reference `components.«component».source` in a rule (by path, can't bind by type for non top level)~~
- ~~`source` node is displayed for each component in the component container~~
- ~~Can reference `components.«component».source.«source set»` in a rule (by path, can't bind by type for non top level)~~
- ~~Can reference `components.«component».source.«source set»` in a rule as a matching specialisation of `LanguageSourceSet`~~
- ~~`source.«sourceSet»` node is displayed for each source set of each component in the component container~~
- ~~Existing usages of `ProjectSourceSet` continue to work, and corresponding root `sources` node (changing anything here is out of scope)~~
- ~~Removal of source sets throws `UnsupportedOperationException`~~

#### Breaking changes

- Removing source sets from components no longer supported

