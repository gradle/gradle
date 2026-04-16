# Fix: Handle super-delegating overrides of upgraded properties

## Context

When a third-party plugin class overrides a getter that was later upgraded to return a more specialized type, Gradle's class generator fails. For example, the FreeFair AspectJ plugin's `AspectjCompile` extends `AbstractCompile` and overrides `FileCollection getClasspath() { return super.getClasspath(); }`. After the upgrade, `AbstractCompile` declares `abstract ConfigurableFileCollection getClasspath()`, so the class generator sees both a concrete getter (old type) and an abstract getter (new type) and fails: *"Cannot have abstract method AbstractCompile.getClasspath(): ConfigurableFileCollection."*

The fix goes in `InstrumentingClassTransform` (bytecode transformation layer). During class loading, we detect the super-delegating override and drop it from the transformed class. The class generator then only sees the abstract getter and handles it correctly.

GitHub issue: https://github.com/gradle/gradle/issues/25421

## Implementation

### Step 1: Add `isReplacedAccessor` to `JvmBytecodeCallInterceptor`

**File:** `platforms/core-runtime/internal-instrumentation-api/src/main/java/org/gradle/internal/instrumentation/api/jvmbytecode/JvmBytecodeCallInterceptor.java`

Add a default method:
```java
default boolean isReplacedAccessor(String owner, String name, String descriptor) {
    return false;
}
```

This checks whether the method `(owner, name, descriptor)` is a replaced accessor of an upgraded property. The interceptor already stores `InstrumentationMetadata` internally for `isInstanceOf` checks, so no extra parameter is needed.

### Step 2: Generate `isReplacedAccessor` in `InterceptJvmCallsGenerator`

**File:** `platforms/core-runtime/internal-instrumentation-processor/src/main/java/org/gradle/internal/instrumentation/processor/codegen/jvmbytecode/InterceptJvmCallsGenerator.java`

In `classContentForClass()` (line 96), add generation of `isReplacedAccessor()` alongside `visitMethodInsn` and `findBridgeMethodBuilder`. Only generate for `BYTECODE_UPGRADE` interceptor types.

The generated code mirrors `visitMethodInsn` but:
- No opcode check (we're checking a declaration, not a callsite)
- No bytecode emission — just `return true`
- Same `metadata.isInstanceOf(owner, containingType)` + name/descriptor matching

Add a new builder method (similar to `getVisitMethodInsnBuilder`/`getFindBridgeMethodBuilder`) and a new code generation method (similar to `generateVisitMethodInsnCode`). Wire it into the builder at line 132-133.

The generated code pattern:
```java
@Override
public boolean isReplacedAccessor(String owner, String name, String descriptor) {
    if (metadata.isInstanceOf(owner, "org/gradle/api/tasks/compile/AbstractCompile")) {
        if (name.equals("getClasspath") && descriptor.equals("()Lorg/gradle/api/file/FileCollection;")) {
            return true;
        }
    }
    // ... other upgraded properties
    return false;
}
```

### Step 3: Modify `InstrumentingVisitor` in `InstrumentingClassTransform`

**File:** `subprojects/core/src/main/java/org/gradle/internal/classpath/transforms/InstrumentingClassTransform.java`

#### 3a: Capture `superName` in `visit()`

In `InstrumentingVisitor.visit()` (line 194), store `this.superName = superName;` (currently not saved).

#### 3b: Add super-delegation detection to `visitMethod()`

Before calling `super.visitMethod()`, check if the method should be dropped:

1. Skip abstract, static, bridge, and synthetic methods
2. Get the `MethodNode` from `classData.readClassAsNode().methods` matching name/descriptor
3. Analyze the MethodNode's `instructions` for the super-delegation pattern:
   - Filter out `LabelNode`, `LineNumberNode`, `FrameNode`
   - Expected: `VarInsnNode(ALOAD, 0)` → `[param loads]` → `MethodInsnNode(INVOKESPECIAL, superName, name, desc)` → optional `TypeInsnNode(CHECKCAST, ...)` → `InsnNode(xRETURN)`
4. Extract the INVOKESPECIAL target `(invokeOwner, invokeName, invokeDescriptor)`
5. Query each interceptor: `interceptor.isReplacedAccessor(invokeOwner, invokeName, invokeDescriptor)`
6. If any returns true, do NOT call `super.visitMethod()` — return a no-op `new MethodVisitor(ASM_LEVEL) {}` to absorb visitor events without emitting the method

### Step 4: Increment `DECORATION_FORMAT`

Same file — change from `38` to `39` to invalidate cached transformations.

### Step 5: Revert `AbstractClassGenerator` changes

**Files to revert/delete:**
- `platforms/core-configuration/model-core/src/main/java/org/gradle/internal/instantiation/generator/AbstractClassGenerator.java` — revert `isSuperDelegatingOverrideOfUpgradedProperty` method and the change to `claimPropertyImplementation`
- `platforms/core-configuration/model-core/src/main/java/org/gradle/internal/instantiation/generator/SuperDelegationDetector.java` — delete

### Step 6: Re-enable smoke test

**File:** `testing/smoke-test/src/smokeTest/groovy/org/gradle/smoketests/FreefairAspectJPluginSmokeTest.groovy`

Remove `@Ignore` annotation and the TODO comment.

## Tests

1. **Code generator test** — extend `InterceptJvmCallsGeneratorTest.groovy` or `PropertyUpgradeCodeGenTest.groovy` to verify `isReplacedAccessor` is generated correctly
2. **Integration test** — test that a class with a super-delegating override of an upgraded property gets the override dropped after transformation. Use existing `AbstractCallInterceptionTest` patterns with `InstrumentedClasses` and `TestInstrumentedClassLoader`
3. **Smoke test** — the re-enabled `FreefairAspectJPluginSmokeTest`

## Verification

1. Run code generator tests: `:internal-instrumentation-processor:test`
2. Run class generator tests: `model-core` tests (to verify no regression)
3. Run the smoke test: `FreefairAspectJPluginSmokeTest`
4. Build with `embeddedIntegTest` from `/Users/asodja/workspace/agents`
