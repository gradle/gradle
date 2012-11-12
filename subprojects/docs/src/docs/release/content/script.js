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

  function addDetailCollapsing(section) {
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
  }

  // Do fixed issues
  var fixedIssues = $("h2#fixed-issues");
  if (fixedIssues.size() > 0) {
    $("<p class='loading-issues'>Retrieving the fixed issue information for @versionBase@ …</p>").insertAfter(fixedIssues);
    var para = $("p.loading-issues")
    var animate = true;
    var paraFadeOut = function() {
      para.fadeOut("80", animate ? paraFadeIn : null);
    };
    var paraFadeIn = function() {
      para.fadeIn("80", animate ? paraFadeOut : null);
    };
    var finishAnimation = function() {
      animate = false;
      para.remove();
    };
    paraFadeOut();

    $.ajax("http://services.gradle.org/fixed-issues/@versionBase@?callback=?", {
      dataType: "jsonp",
      cache: true,
      success: function(data, textStatus, jqXHR) {
        finishAnimation();
        var para = $("<p>" + data.length + " issues have been fixed in Gradle @versionBase@.</p>").insertAfter(fixedIssues);
        if (data.length > 0) {
          var list = $("<ul id='fixed-issues-list'></ul>").hide().insertAfter(para)
          $.each(data, function (i, issue) {
            $("<li>[<a href='" + issue["link"] + "'>"+ issue["key"] + "</a>] - " + issue["summary"] + "</li>").appendTo(list);
          });
          list.slideDown("slow");
        }
      },
      timeout: 10000,
      error: function() {
        finishAnimation();
        $("<p>Unable to retrieve the fixed issue information. You may not be connected to the Internet, or there may have been an error.</p>").insertAfter(fixedIssues).css({fontWeight: "bold", color: "red"});
      }
    });
  }

  $("section.major-detail").each(function() {
    addDetailCollapsing($(this));
  });


});