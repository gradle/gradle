package org.gradle.internal.upgrade;

import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.gradle.internal.hash.Hasher;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.SWAP;

/**
 * Replaces a method call with alternative code.
 * <p>
 * Replaces both statically compiled calls and dynamic Groovy call sites.
 */
class MethodReplacement<T> implements Replacement {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodReplacement.class);

    private static final String INVOKE_REPLACEMENT_DESC;

    static {
        try {
            INVOKE_REPLACEMENT_DESC = Type.getMethodDescriptor(ApiUpgradeHandler.class.getMethod("invokeReplacement", Object.class, Object[].class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final Type type;
    private final String methodName;
    private final Type[] argumentTypes;
    private final String methodDescriptor;
    private final ReplacementLogic<T> replacement;

    public MethodReplacement(Type type, Type returnType, String methodName, Type[] argumentTypes, ReplacementLogic<T> replacement) {
        this.type = type;
        this.methodName = methodName;
        this.argumentTypes = argumentTypes;
        this.methodDescriptor = Type.getMethodDescriptor(returnType, argumentTypes);
        this.replacement = replacement;
    }

    public T invokeReplacement(Object receiver, Object[] arguments) {
        LOGGER.info("Calling replacement for {}.{}({})", type, methodName, methodDescriptor);
        return replacement.execute(receiver, arguments);
    }

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(MethodReplacement.class.getName());
        hasher.putString(type.getDescriptor());
        hasher.putString(methodName);
        hasher.putString(methodDescriptor);
        // TODO: Can we capture the replacements as well?
    }

    @Override
    public boolean replaceByteCodeIfMatches(int opcode, String owner, String name, String desc, boolean itf, int index, MethodVisitor mv) {
        if (opcode == INVOKEVIRTUAL
            && owner.equals(type.getInternalName())
            && name.equals(methodName)
            && desc.equals(methodDescriptor)) {

            LOGGER.info("Matched {}.{}({}), replacing...", owner, name, desc);

            // Create Object[] for arguments
            // STACK: this, ... -> this, ..., length
            mv.visitLdcInsn(argumentTypes.length);
            // STACK: this, ..., length -> this, ..., []
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

            // Convert the arguments on stack to Object[]
            for (int argumentIndex = argumentTypes.length - 1; argumentIndex >= 0; argumentIndex--) {
                Type argumentType = argumentTypes[argumentIndex];
                // Convert argumetn on stack behind Object[] to Object if necessary
                // STACK: this, ..., arg as primitve, [] -> this, ..., arg as Object, []
                boxParameterIfNecessary(mv, argumentType);
                // STACK: this, ..., arg, [] -> this, ..., arg, [], argIndex
                mv.visitLdcInsn(argumentIndex);
                // STACK: this, ..., arg, [], argIndex -> this, ..., [], argIndex, arg, [], argIndex
                mv.visitInsn(DUP2_X1);
                // STACK: this, ..., [], argIndex, arg, [], argIndex -> this, ..., [], argIndex, arg, []
                mv.visitInsn(POP);
                // STACK: this, ..., [], argIndex, arg, [] -> this, ..., [], [], argIndex, arg, []
                mv.visitInsn(DUP_X2);
                // STACK: this, ..., [], [], argIndex, arg, [] -> this, ..., [], [], argIndex, arg
                mv.visitInsn(POP);
                // STACK: this, ..., [], [], argIndex, arg -> this, ..., []
                mv.visitInsn(AASTORE);
            }

            // Put the replacement index on stack
            // STACK: this, [] -> this, [], index
            mv.visitLdcInsn(index);

            // Call the replacement method
            // STACK: this, [], index -> result as Object
            mv.visitMethodInsn(
                INVOKESTATIC,
                Type.getInternalName(ApiUpgradeHandler.class),
                "invokeReplacement",
                INVOKE_REPLACEMENT_DESC,
                false);

            // Re-cast the returned value
            // STACK: result as Object -> result as T
            Type returnType = Type.getReturnType(desc);
            unboxReturnTypeIfNecessary(mv, returnType);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Optional<CallSite> decorateCallSite(CallSite callSite) {
        if (callSite.getName().equals(methodName)) {
            return Optional.of(new AbstractCallSite(callSite) {
                @Override
                public Object call(Object receiver, Object[] args) {
                    return replacement.execute(receiver, args);
                }
            });
        } else {
            return Optional.empty();
        }
    }

    // TODO Test this
    private static void boxParameterIfNecessary(MethodVisitor mv, Type type) {
        Type primitiveType;
        Type objectType;
        int sort = type.getSort();
        switch (sort) {
            case Type.BOOLEAN:
                primitiveType = Type.BOOLEAN_TYPE;
                objectType = Type.getType(Boolean.class);
                break;
            case Type.BYTE:
                primitiveType = Type.BYTE_TYPE;
                objectType = Type.getType(Byte.class);
                break;
            case Type.CHAR:
                primitiveType = Type.CHAR_TYPE;
                objectType = Type.getType(Character.class);
                break;
            case Type.SHORT:
                primitiveType = Type.SHORT_TYPE;
                objectType = Type.getType(Short.class);
                break;
            case Type.INT:
                primitiveType = Type.INT_TYPE;
                objectType = Type.getType(Integer.class);
                break;
            case Type.FLOAT:
                primitiveType = Type.FLOAT_TYPE;
                objectType = Type.getType(Float.class);
                break;
            case Type.LONG:
                primitiveType = Type.LONG_TYPE;
                objectType = Type.getType(Long.class);
                break;
            case Type.DOUBLE:
                primitiveType = Type.DOUBLE_TYPE;
                objectType = Type.getType(Double.class);
                break;
            default:
                return;
        }

        // Swap the Object[] and the unboxed arg on the operand stack
        if (type.getSize() == 2) {
            // STACK: this, ..., arg1, arg2, [] -> this, ..., [], arg1, arg2, []
            mv.visitInsn(DUP_X2);
            // STACK: this, ..., [], arg1, arg2, [] -> this, ..., [], arg1, arg2
            mv.visitInsn(POP);
        } else {
            // STACK: this, ..., arg, [] -> this, ..., [], arg
            mv.visitInsn(SWAP);
        }

        // Box the primitive value
        // STACK: this, ..., [], arg as primitive -> this, ..., [], arg as object
        mv.visitMethodInsn(INVOKESTATIC, objectType.getInternalName(), "valueOf", Type.getMethodDescriptor(objectType, primitiveType), false);

        // Swap the boxed arg and the Object[] back in place
        // STACK: this, ..., [], arg -> this, ..., arg, []
        mv.visitInsn(SWAP);
    }

    // TODO Test this
    private static void unboxReturnTypeIfNecessary(MethodVisitor mv, Type type) {
        Type primitiveType;
        String methodName;
        Type objectType;
        int sort = type.getSort();
        switch (sort) {
            case Type.BOOLEAN:
                primitiveType = Type.BOOLEAN_TYPE;
                methodName = "booleanValue";
                objectType = Type.getType(Boolean.class);
                break;
            case Type.BYTE:
                primitiveType = Type.BYTE_TYPE;
                methodName = "byteValue";
                objectType = Type.getType(Byte.class);
                break;
            case Type.CHAR:
                primitiveType = Type.CHAR_TYPE;
                methodName = "charValue";
                objectType = Type.getType(Character.class);
                break;
            case Type.SHORT:
                primitiveType = Type.SHORT_TYPE;
                methodName = "shortValue";
                objectType = Type.getType(Short.class);
                break;
            case Type.INT:
                primitiveType = Type.INT_TYPE;
                methodName = "intValue";
                objectType = Type.getType(Integer.class);
                break;
            case Type.FLOAT:
                primitiveType = Type.FLOAT_TYPE;
                methodName = "floatValue";
                objectType = Type.getType(Float.class);
                break;
            case Type.LONG:
                primitiveType = Type.LONG_TYPE;
                methodName = "longValue";
                objectType = Type.getType(Long.class);
                break;
            case Type.DOUBLE:
                primitiveType = Type.DOUBLE_TYPE;
                methodName = "doubleValue";
                objectType = Type.getType(Double.class);
                break;
            case Type.VOID:
                // Pop the null object pushed by invokeReplacement()
                mv.visitInsn(POP);
                return;
            default:
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
                return;
        }

        // Make sure the top of the operand stack holds the right type
        mv.visitTypeInsn(CHECKCAST, objectType.getInternalName());

        // Unbox the primitive value
        // STACK: this, ..., [], arg as object -> this, ..., [], arg as primitive
        mv.visitMethodInsn(INVOKEVIRTUAL, objectType.getInternalName(), methodName, Type.getMethodDescriptor(primitiveType), false);
    }
}
