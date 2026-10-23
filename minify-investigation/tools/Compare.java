import org.objectweb.asm.*; import org.objectweb.asm.util.*; import java.io.*; import java.util.*; import java.util.zip.*;
// Compare two jars: entry sets, class bodies (ignoring frames), and frame presence
public class Compare {
  static Map<String, byte[]> read(String f) throws IOException {
    Map<String, byte[]> m = new TreeMap<>();
    try (ZipFile z = new ZipFile(f)) { for (var e : Collections.list(z.entries())) if (!e.isDirectory()) m.put(e.getName(), z.getInputStream(e).readAllBytes()); }
    return m;
  }
  static String text(byte[] b) { StringWriter w = new StringWriter(); new ClassReader(b).accept(new TraceClassVisitor(new PrintWriter(w)), ClassReader.SKIP_FRAMES); return w.toString(); }
  // methods with branches but no StackMapTable (class version >= 50)
  static int missingFrames(byte[] b) {
    int[] n = {0}; ClassReader r = new ClassReader(b);
    r.accept(new ClassVisitor(Opcodes.ASM9) {
      int version;
      public void visit(int v, int a, String nm, String s, String sup, String[] i) { version = v & 0xFFFF; }
      public MethodVisitor visitMethod(int a, String nm, String d, String s, String[] e) {
        return new MethodVisitor(Opcodes.ASM9) {
          boolean jumps, frames;
          public void visitJumpInsn(int o, Label l) { jumps = true; }
          public void visitTableSwitchInsn(int a1, int b1, Label d1, Label... l) { jumps = true; }
          public void visitLookupSwitchInsn(Label d1, int[] k, Label[] l) { jumps = true; }
          public void visitTryCatchBlock(Label s1, Label e1, Label h, String t) { jumps = true; }
          public void visitFrame(int t, int nl, Object[] l, int ns, Object[] st) { frames = true; }
          public void visitEnd() { if (version >= 50 && jumps && !frames) n[0]++; }
        };
      }
    }, 0);
    return n[0];
  }
  public static void main(String[] a) throws Exception {
    var x = read(a[0]); var y = read(a[1]);
    var onlyX = new TreeSet<>(x.keySet()); onlyX.removeAll(y.keySet());
    var onlyY = new TreeSet<>(y.keySet()); onlyY.removeAll(x.keySet());
    System.out.println("entries " + x.size() + " vs " + y.size() + ", only in first " + onlyX.size() + ", only in second " + onlyY.size());
    onlyX.stream().limit(5).forEach(e -> System.out.println("  < " + e)); onlyY.stream().limit(5).forEach(e -> System.out.println("  > " + e));
    int diffClass = 0, diffRes = 0, missX = 0, missY = 0;
    for (var e : x.entrySet()) {
      byte[] o = y.get(e.getKey()); if (o == null) continue;
      if (e.getKey().endsWith(".class")) {
        missX += missingFrames(e.getValue()); missY += missingFrames(o);
        if (!text(e.getValue()).equals(text(o))) { if (diffClass++ < 3) System.out.println("  class differs: " + e.getKey()); }
      } else if (!Arrays.equals(e.getValue(), o)) { if (diffRes++ < 3) System.out.println("  resource differs: " + e.getKey()); }
    }
    System.out.println("classes differing (frames ignored): " + diffClass + ", resources differing: " + diffRes);
    System.out.println("methods missing StackMapTable: first " + missX + ", second " + missY);
  }
}
