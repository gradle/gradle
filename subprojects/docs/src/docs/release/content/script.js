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

  function injectIssues(url, insertAfter, idBase, loadingText, messageFunction) {
    var loadingPara = $("<p class='" + idBase + "-loading'>" + loadingText + " …</p>").insertAfter(insertAfter);
    var animate = true;
    var paraFadeOut = function() {
      loadingPara.fadeOut("80", animate ? paraFadeIn : null);
    };
    var paraFadeIn = function() {
      loadingPara.fadeIn("80", animate ? paraFadeOut : null);
    };
    var finishAnimation = function() {
      animate = false;
      loadingPara.remove();
    };
    paraFadeOut();

    $.ajax(url + "?callback=?", {
      dataType: "jsonp",
      cache: true,
      success: function(data, textStatus, jqXHR) {
        finishAnimation();
        var para = $("<p>" + messageFunction(data.length) + "</p>").insertAfter(insertAfter);
        if (data.length > 0) {
          var list = $("<ul id='" + idBase + "-list'></ul>").hide().insertAfter(para);
          $.each(data, function (i, issue) {
            $("<li>[<a href='" + issue["link"] + "'>"+ issue["key"] + "</a>] - " + issue["summary"] + "</li>").appendTo(list);
          });
          list.slideDown("slow");
        }
      },
      timeout: 10000,
      error: function() {
        finishAnimation();
        $("<p>Unable to retrieve the issue information. You may not be connected to the Internet, or there may have been an error.</p>").insertAfter(insertAfter).css({fontWeight: "bold", color: "red"});
      }
    });
  }
  
  injectIssues(
    "http://services.gradle.org/fixed-issues/@versionBase@", 
    $("h2#fixed-issues"), 
    "fixed-issues", 
    "Retrieving the fixed issue information for @versionBase@", 
    function(i) {
      return i + " issues have been fixed in Gradle @versionBase@.";
    }
  );
  
  injectIssues(
    "http://services.gradle.org/known-issues/@versionBase@", 
    $("h2#known-issues").next("p"), 
    "known-issues", 
    "Retrieving the known issue information for @versionBase@", 
    function(i) {
      if (i > 0) {
        return i + " issues have been fixed in Gradle @versionBase@.";
      } else {
        return "There are no known issues of Gradle @versionBase@ at this time.";
      }
    } 
  );

  $("section.major-detail").each(function() {
    addDetailCollapsing($(this));
  });

});