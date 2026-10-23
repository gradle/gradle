import java.net.URI; import java.nio.file.*; import java.util.*; import java.util.zip.*; import java.io.*;
public class Platform { public static void main(String[] a) throws Exception {
  Path modules = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules");
  Set<String> written = new HashSet<>();
  try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(a[0])));
       var paths = Files.walk(modules)) {
    for (Path p : (Iterable<Path>) paths.filter(x -> x.toString().endsWith(".class"))::iterator) {
      String rel = modules.relativize(p).toString(); String name = rel.substring(rel.indexOf('/') + 1);
      if (!name.equals("module-info.class") && written.add(name)) { out.putNextEntry(new ZipEntry(name)); Files.copy(p, out); out.closeEntry(); }
    }
  }
}}
