# Dependent components


## Summary

This spec defines a number of features to allows a developer to operate on builds based on components reverse dependency graph. A report of components that depends on a given component provide insight into the reverse dependency graph. Tasks allows to trigger the build of a given component and of all the components that depends on it. A DSL allows to declare ad-hoc components for the sake of integrating ad-hoc built software components in the reverse dependency graph. 


## Features

- [ ] [Report of all native components which depend on a given native component](dependent-components-report) 
- [ ] [Build all components that depend on a given component](build-dependent-components)
- [ ] [Define ad-hoc components and their inputs and outputs](ad-hoc-dependent-components)


## General implementation notes

All native components must be supported.
Support for other types of components is a plus.


## Out of scope

- Support for Play components.
