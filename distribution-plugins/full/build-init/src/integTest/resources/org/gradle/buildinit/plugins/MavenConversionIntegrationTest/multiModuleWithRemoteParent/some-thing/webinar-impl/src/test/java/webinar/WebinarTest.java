package webinar;

import org.junit.Assert;
import org.junit.Test;

public class WebinarTest {
  
  @Test public void normalizesDescription() {
    //when
    Demoable demoable = new Webinar("nice   day");
    
    //then
    Assert.assertEquals("nice day", demoable.getDescription());
  }
}