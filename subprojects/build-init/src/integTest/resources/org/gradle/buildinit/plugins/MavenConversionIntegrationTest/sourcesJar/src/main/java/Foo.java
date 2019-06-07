import org.apache.commons.lang3.StringUtils;

public class Foo {
  public String toString() {
    return StringUtils.normalizeSpace("hi  there!");
  }
}
