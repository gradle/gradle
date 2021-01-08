import org.apache.commons.lang.StringUtils;

public class Foo {
  public String toString() {
    return StringUtils.normalizeSpace("hi  there!");
  }
}