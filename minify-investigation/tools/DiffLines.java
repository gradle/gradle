import org.objectweb.asm.*; import org.objectweb.asm.util.*; import java.io.*; import java.util.*; import java.util.zip.*;
// Histogram of textified lines that differ between same-named classes of two jars
public class DiffLines {
  static String text(byte[] b) { StringWriter w = new StringWriter(); new ClassReader(b).accept(new TraceClassVisitor(new PrintWriter(w)), ClassReader.SKIP_FRAMES); return w.toString(); }
  public static void main(String[] a) throws Exception {
    Map<String, Integer> hist = new HashMap<>(); int[] cls = {0};
    try (ZipFile x = new ZipFile(a[0]); ZipFile y = new ZipFile(a[1])) {
      for (ZipEntry e : Collections.list(x.entries())) {
        if (!e.getName().endsWith(".class")) continue; ZipEntry f = y.getEntry(e.getName()); if (f == null) continue;
        List<String> l1 = Arrays.asList(text(x.getInputStream(e).readAllBytes()).split("\n")), l2 = Arrays.asList(text(y.getInputStream(f).readAllBytes()).split("\n"));
        if (l1.equals(l2)) continue; cls[0]++;
        Map<String,Integer> c = new HashMap<>(); for (String s : l1) c.merge(s.trim(), 1, Integer::sum); for (String s : l2) c.merge(s.trim(), -1, Integer::sum);
        c.forEach((k, v) -> { if (v != 0) hist.merge((v > 0 ? "< " : "> ") + k.replaceAll("\"[^\"]*\"", "\"…\"").replaceAll("L\\d+", "L#"), Math.abs(v), Integer::sum); });
      }
    }
    System.out.println("differing classes: " + cls[0]);
    hist.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(25).forEach(en -> System.out.printf("%7d %s%n", en.getValue(), en.getKey()));
  }
}
