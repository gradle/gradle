$(function() {
  $("section.major-detail").each(function() {
    var section = $(this);
    section.hide();
    var buttonParagraph = $("<p><button class='display-toggle'>Show more information…</button></p>").insertAfter(section);
    buttonParagraph.find("button").click(function() {
      var button = $(this);
      var hiding = section.is(":visible");

      var toggle = function() {
        section.slideToggle("slow", function() {
          button.text(hiding ? "Show more information…" : "Show less information");
        });
      };

      if (hiding) {
        var i = 0;
        $('html,body').animate({
          scrollTop: section.prevAll("h3:first").offset().top
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