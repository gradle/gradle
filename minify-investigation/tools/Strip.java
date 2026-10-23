import org.objectweb.asm.*; import java.io.*; import java.util.*; import java.util.zip.*;
// Strip.java in.jar out.jar pkg/prefix/ ... : drops signature files and the given packages, and LocalVariable(Type)Tables
public class Strip {
  public static void main(String[] a) throws Exception {
    List<String> removed = Arrays.asList(a).subList(2, a.length);
    try (ZipFile in = new ZipFile(a[0]); ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(a[1]), 1 << 16))) {
      for (ZipEntry e : Collections.list(in.entries())) {
        String n = e.getName();
        if (n.matches("META-INF/[^/]*\\.(SF|RSA|DSA|EC)")) continue;
        if (removed.stream().anyMatch(p -> n.startsWith(p) || n.contains("/" + p))) continue;
        byte[] b = in.getInputStream(e).readAllBytes();
        if (n.endsWith(".class")) {
          ClassReader r = new ClassReader(b); ClassWriter w = new ClassWriter(0);
          r.accept(new ClassVisitor(Opcodes.ASM9, w) {
            public MethodVisitor visitMethod(int acc, String nm, String d, String s, String[] ex) {
              return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, nm, d, s, ex)) {
                public void visitLocalVariable(String n1, String d1, String s1, Label st, Label en, int i) {}
              };
            }
          }, 0);
          b = w.toByteArray();
        }
        ZipEntry o = new ZipEntry(n); o.setTime(e.getTime()); out.putNextEntry(o); out.write(b); out.closeEntry();
      }
    }
  }
}
