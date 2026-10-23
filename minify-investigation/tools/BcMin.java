import org.objectweb.asm.*; import java.io.*; import java.util.*; import java.util.regex.*; import java.util.zip.*;
// Class-level reachability minifier for bcprov, mirroring the MinifySpec in gradlebuild.minify.gradle.kts.
// BcMin in.jar out.jar <versions: drop|keep> <loadPQCKeys: keep|stub>
public class BcMin {
  static Pattern glob(String g) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < g.length(); i++) {
      char c = g.charAt(i);
      if (c == '*') { if (i + 1 < g.length() && g.charAt(i + 1) == '*') { b.append(".*"); i++; } else b.append("[^.]*"); }
      else if (c == '.' || c == '$') b.append('\\').append(c); else b.append(c);
    }
    return Pattern.compile(b.toString());
  }
  static final List<String> KEEP = List.of("org.bouncycastle.**", "org.bouncycastle.pqc.crypto.mlkem.**", "org.bouncycastle.pqc.crypto.ntruprime.**");
  static final List<String> EXCL = List.of("org.bouncycastle.pqc.**", "org.bouncycastle.jcajce.provider.asymmetric.Dilithium**", "org.bouncycastle.jcajce.provider.asymmetric.Falcon**",
    "org.bouncycastle.jcajce.provider.asymmetric.NTRU**", "org.bouncycastle.jcajce.provider.asymmetric.SLHDSA**", "org.bouncycastle.jcajce.provider.asymmetric.SPHINCSPlus**",
    "org.bouncycastle.jcajce.provider.asymmetric.mldsa.**", "org.bouncycastle.jcajce.provider.asymmetric.slhdsa.**", "org.bouncycastle.jcajce.provider.asymmetric.mlkem.**",
    "org.bouncycastle.crypto.signers.mldsa.**", "org.bouncycastle.crypto.signers.slhdsa.**", "org.bouncycastle.crypto.signers.Hash**",
    "org.bouncycastle.crypto.signers.SLHDSASigner", "org.bouncycastle.crypto.kems.mlkem.**");
  static final List<String> REMOVED = List.of("org/bouncycastle/pqc/jcajce/", "org/bouncycastle/pqc/legacy/");

  static boolean isRoot(String dotted) {
    for (String k : KEEP) {
      if (!glob(k).matcher(dotted).matches()) continue;
      boolean excluded = false;
      for (String e : EXCL) if (!k.startsWith(e.substring(0, e.indexOf('*') < 0 ? e.length() : e.indexOf('*'))) && glob(e).matcher(dotted).matches()) excluded = true;
      if (!excluded) return true;
    }
    return false;
  }

  static Set<String> refs(byte[] b) {
    Set<String> out = new HashSet<>();
    class T { void type(Type t) { if (t.getSort() == Type.ARRAY) t = t.getElementType(); if (t.getSort() == Type.OBJECT) out.add(t.getInternalName()); else if (t.getSort() == Type.METHOD) { type(t.getReturnType()); for (Type a : t.getArgumentTypes()) type(a); } }
              void desc(String d) { type(Type.getType(d)); } void name(String n) { if (n != null) type(n.startsWith("[") ? Type.getType(n) : Type.getObjectType(n)); }
              void cst(Object o) { if (o instanceof Type t) type(t); else if (o instanceof Handle h) { name(h.getOwner()); desc(h.getDesc()); } else if (o instanceof ConstantDynamic c) { desc(c.getDescriptor()); cst(c.getBootstrapMethod()); for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++) cst(c.getBootstrapMethodArgument(i)); } } }
    T t = new T();
    AnnotationVisitor av = new AnnotationVisitor(Opcodes.ASM9) {
      public void visit(String n, Object v) { t.cst(v); } public void visitEnum(String n, String d, String v) { t.desc(d); }
      public AnnotationVisitor visitAnnotation(String n, String d) { t.desc(d); return this; } public AnnotationVisitor visitArray(String n) { return this; } };
    new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
      public void visit(int v, int a, String n, String s, String sup, String[] itf) { t.name(sup); if (itf != null) for (String i : itf) t.name(i); }
      public void visitOuterClass(String o, String n, String d) { t.name(o); }
      public void visitNestHost(String h) { t.name(h); }
      public AnnotationVisitor visitAnnotation(String d, boolean vis) { t.desc(d); return av; }
      public FieldVisitor visitField(int a, String n, String d, String s, Object v) { t.desc(d); return new FieldVisitor(Opcodes.ASM9) { public AnnotationVisitor visitAnnotation(String d2, boolean v2) { t.desc(d2); return av; } }; }
      public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
        t.desc(d); if (ex != null) for (String e : ex) t.name(e);
        return new MethodVisitor(Opcodes.ASM9) {
          public AnnotationVisitor visitAnnotation(String d2, boolean v2) { t.desc(d2); return av; }
          public void visitTypeInsn(int o, String ty) { t.name(ty); }
          public void visitFieldInsn(int o, String ow, String n2, String d2) { t.name(ow); t.desc(d2); }
          public void visitMethodInsn(int o, String ow, String n2, String d2, boolean i) { t.name(ow); t.desc(d2); }
          public void visitInvokeDynamicInsn(String n2, String d2, Handle bsm, Object... args) { t.desc(d2); t.cst(bsm); for (Object x : args) t.cst(x); }
          public void visitLdcInsn(Object v) { t.cst(v); }
          public void visitMultiANewArrayInsn(String d2, int dims) { t.desc(d2); }
          public void visitTryCatchBlock(Label s1, Label e1, Label h, String ty) { t.name(ty); }
        };
      }
    }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    return out;
  }

  static byte[] rewrite(String name, byte[] b, boolean stubPqc) {
    ClassReader r = new ClassReader(b); ClassWriter w = new ClassWriter(0);
    r.accept(new ClassVisitor(Opcodes.ASM9, w) {
      public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
        MethodVisitor mv = super.visitMethod(a, n, d, s, ex);
        if (stubPqc && name.equals("org/bouncycastle/jce/provider/BouncyCastleProvider") && n.equals("loadPQCKeys")) {
          mv.visitCode(); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 1); mv.visitEnd(); return null;
        }
        return new MethodVisitor(Opcodes.ASM9, mv) { public void visitLocalVariable(String n1, String d1, String s1, Label st, Label en, int i) {} };
      }
    }, 0);
    return w.toByteArray();
  }

  public static void main(String[] a) throws Exception {
    boolean keepVersions = a[2].equals("keep"), stub = a[3].equals("stub");
    Map<String, byte[]> classes = new LinkedHashMap<>(); Map<String, byte[]> versioned = new LinkedHashMap<>(); Map<String, byte[]> resources = new LinkedHashMap<>();
    try (ZipFile in = new ZipFile(a[0])) {
      for (ZipEntry e : Collections.list(in.entries())) {
        String n = e.getName(); if (e.isDirectory() || n.matches("META-INF/[^/]*\\.(SF|RSA|DSA|EC)")) continue;
        String base = n.replaceFirst("^META-INF/versions/\\d+/", "");
        if (REMOVED.stream().anyMatch(base::startsWith)) continue;
        byte[] b = in.getInputStream(e).readAllBytes();
        if (base.equals("module-info.class")) continue;
        if (!n.endsWith(".class")) resources.put(n, b);
        else if (!n.equals(base)) versioned.put(n, b); else classes.put(n.substring(0, n.length() - 6), b);
      }
    }
    if (stub) { String p = "org/bouncycastle/jce/provider/BouncyCastleProvider"; classes.put(p, rewrite(p, classes.get(p), true)); }
    Map<String, Set<String>> versionRefs = new HashMap<>();
    if (keepVersions) for (var e : versioned.entrySet()) versionRefs.computeIfAbsent(e.getKey().replaceFirst("^META-INF/versions/\\d+/", "").replace(".class", ""), k -> new HashSet<>()).addAll(refs(e.getValue()));
    Deque<String> work = new ArrayDeque<>(); Set<String> kept = new HashSet<>();
    for (String c : classes.keySet()) if (isRoot(c.replace('/', '.'))) { kept.add(c); work.add(c); }
    // version-only classes (no base counterpart) are reachable only via their versioned siblings; treat matching keep roots as roots too
    if (keepVersions) for (String c : versionRefs.keySet()) if (!classes.containsKey(c) && isRoot(c.replace('/', '.'))) { kept.add(c); work.add(c); }
    while (!work.isEmpty()) {
      String c = work.poll(); Set<String> rs = new HashSet<>();
      if (classes.containsKey(c)) rs.addAll(refs(classes.get(c)));
      rs.addAll(versionRefs.getOrDefault(c, Set.of()));
      for (String r : rs) if ((classes.containsKey(r) || versionRefs.containsKey(r)) && kept.add(r)) work.add(r);
    }
    try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(a[1]), 1 << 16))) {
      out.setLevel(9);
      for (var e : resources.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); }
      for (var e : classes.entrySet()) if (kept.contains(e.getKey())) { out.putNextEntry(new ZipEntry(e.getKey() + ".class")); out.write(rewrite(e.getKey(), e.getValue(), false)); }
      if (keepVersions) for (var e : versioned.entrySet()) if (kept.contains(e.getKey().replaceFirst("^META-INF/versions/\\d+/", "").replace(".class", ""))) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(rewrite(e.getKey(), e.getValue(), false)); }
    }
    System.out.println("kept classes: " + kept.size());
  }
}
