
### Story: Only attach source sets of relevant languages to component

- Don't attach Java source sets to native components.
- Don't attach native language source sets to jvm components.

This story will involve defining 'input-type' for each component type: e.g. JvmByteCode for a JvmLibraryBinary and ObjectFile for NativeBinary.
A language plugin will need to register the compiled output type for each source set. Then it will be possible for a component to only
attach to those language source sets that have an appropriate output type.
