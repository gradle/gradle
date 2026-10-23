import org.objectweb.asm.*; import java.util.*; import java.util.zip.*;
// Counts methods that still have a LocalVariableTable
public class Lvt { public static void main(String[] a) throws Exception { int[] n = {0};
  try (ZipFile z = new ZipFile(a[0])) { for (var e : Collections.list(z.entries())) if (e.getName().endsWith(".class"))
    new ClassReader(z.getInputStream(e).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) { public MethodVisitor visitMethod(int ac, String nm, String d, String s, String[] ex) {
      return new MethodVisitor(Opcodes.ASM9) { boolean seen; public void visitLocalVariable(String n1, String d1, String s1, Label st, Label en, int i) { if (!seen) { seen = true; n[0]++; } } }; } }, 0); }
  System.out.println("methods with LocalVariableTable: " + n[0]); } }
