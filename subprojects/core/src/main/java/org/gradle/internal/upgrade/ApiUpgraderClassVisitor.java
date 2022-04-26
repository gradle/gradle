package org.gradle.internal.upgrade;

import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

class ApiUpgraderClassVisitor extends ClassVisitor {

    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";
    private static final String RETURN_CALL_SITE_ARRAY = getMethodDescriptor(getType(CallSiteArray.class));
    private static final Type API_UPGRADE_HANDLER_TYPE = getType(ApiUpgradeHandler.class);
    private static final String DECORATED_CREATE_CALL_SITE_ARRAY_METHoD = "$decoratedCreateCallSiteArray";
    private static final String CALL_SITE_DECORATOR_METHOD = "decorateCallSiteArray";
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY = getMethodDescriptor(Type.VOID_TYPE, getType(CallSiteArray.class));
    private static final String[] NO_EXCEPTIONS = new String[0];

    private final List<Replacement> replacements;
    private boolean hasGroovyCallSites;
    private String className;

    public ApiUpgraderClassVisitor(List<Replacement> methodReplacements, ClassVisitor classVisitor) {
        super(ASM9, classVisitor);
        this.replacements = methodReplacements;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (cv == null) {
            return null;
        }

        if (name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && desc.equals(RETURN_CALL_SITE_ARRAY)) {
            hasGroovyCallSites = true;
        }

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodReplaceMethodVisitor(mv);
    }

    @Override
    public void visitEnd() {
        if (hasGroovyCallSites) {
            generateCallSiteFactoryMethod();
        }
        super.visitEnd();
    }

    private void generateCallSiteFactoryMethod() {
        MethodVisitor methodVisitor = super.visitMethod(
            ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE,
            DECORATED_CREATE_CALL_SITE_ARRAY_METHoD,
            RETURN_CALL_SITE_ARRAY,
            null,
            NO_EXCEPTIONS
        );
        methodVisitor.visitCode();
        methodVisitor.visitMethodInsn(INVOKESTATIC, className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY, false);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESTATIC, API_UPGRADE_HANDLER_TYPE.getInternalName(), CALL_SITE_DECORATOR_METHOD, RETURN_VOID_FROM_CALL_SITE_ARRAY, false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(2, 0);
        methodVisitor.visitEnd();
    }

    private final class MethodReplaceMethodVisitor extends MethodVisitor {

        public MethodReplaceMethodVisitor(MethodVisitor mv) {
            super(ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            for (int index = 0, len = replacements.size(); index < len; index++) {
                Replacement replacement = replacements.get(index);
                if (replacement.replaceByteCodeIfMatches(opcode, owner, name, desc, itf, index, this)) {
                    return;
                }
            }

            if (owner.equals(className) && name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && desc.equals(RETURN_CALL_SITE_ARRAY)) {
                super.visitMethodInsn(INVOKESTATIC, className, DECORATED_CREATE_CALL_SITE_ARRAY_METHoD, RETURN_CALL_SITE_ARRAY, false);
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
}
