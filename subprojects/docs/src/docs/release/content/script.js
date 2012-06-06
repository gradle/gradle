$(function() {
  function elementInViewport(el) {
    var rect = el.getBoundingClientRect();

    return (
      rect.top >= 0 &&
      rect.left >= 0 &&
      rect.bottom <= window.innerHeight &&
      rect.right <= window.innerWidth 
    );
  }
  
  
  $("section.major-detail").each(function() {
    var section = $(this);
    section.hide();
    var buttonParagraph = $("<p><button class='display-toggle'>More »</button></p>").insertAfter(section);
    buttonParagraph.find("button").click(function() {
      var button = $(this);
      var hiding = section.is(":visible");

      var toggle = function() {
        section.slideToggle("slow", function() {
          button.text(hiding ? "More »" : "« Less");
        });
      };
      
      var header = section.prevAll("h3:first");
      
      if (hiding && !elementInViewport(header.get(0))) {
        var i = 0;
        $('html,body').animate({
          scrollTop: header.offset().top
        }, "fast", "swing", function() {
          if (++i == 2) {
            toggle();
          }
        });
      } else {
        toggle();
      }
    });
  });
});