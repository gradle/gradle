package webinar;

import org.apache.commons.lang.StringUtils;

public class Webinar implements Demoable {
  
  private final String description;
  
  public Webinar() {
    this("I'm happy today!");
  }
  
  public Webinar(String description) {
    this.description = description;
  }

  public String getDescription() {
    return StringUtils.normalizeSpace(description);
  }
}
