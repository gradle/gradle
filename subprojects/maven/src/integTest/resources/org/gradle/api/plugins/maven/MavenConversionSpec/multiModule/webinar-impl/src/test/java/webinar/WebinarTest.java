package webinar;

import org.junit.Test;

public class WebinarTest {
  
  @Test public void normalizesDescription() {
    //when
    Demoable demoable = new Webinar("nice   day");
    
    //then
    assertEquals("nice day", demoable.getDescription());
  }
}