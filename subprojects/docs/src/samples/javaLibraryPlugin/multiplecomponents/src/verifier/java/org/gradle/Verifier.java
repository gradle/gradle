package org.gradle;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public class Verifier {
    public void verify(String className) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(className).accept(cn, ClassReader.SKIP_DEBUG);
        for (Object method : cn.methods) {
            Analyzer a = new Analyzer(new SimpleVerifier());
            a.analyze(cn.name, (MethodNode)method);
        }
    }
}
