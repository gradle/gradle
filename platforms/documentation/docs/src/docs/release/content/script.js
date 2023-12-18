$(function() {
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

    $.ajax(url, {
      dataType: "json",
      cache: true,
      success: function(data, textStatus, jqXHR) {
        finishAnimation();
        var para = $("<p>" + messageFunction(data.length) + "</p>").insertAfter(insertAfter);
        if (data.length > 0) {
          var list = $("<ul id='" + idBase + "-list'></ul>").hide().insertAfter(para);
          $.each(data, function (i, issue) {
            var link = $("<a></a>").attr("href", issue["link"]).text(issue["key"]);
            $("<li></li>").append(document.createTextNode("["), link, document.createTextNode("] - " + issue["summary"])).appendTo(list);
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
    "https://services.gradle.org/fixed-issues/@baseVersion@",
    $("h2#fixed-issues"),
    "fixed-issues",
    "Retrieving the fixed issue information for @baseVersion@",
    function(i) {
      return i + " issues have been fixed in Gradle @baseVersion@.";
    }
  );

  injectIssues(
    "https://services.gradle.org/known-issues/@baseVersion@",
    $("h2#known-issues").next("p"),
    "known-issues",
    "Retrieving the known issue information for @baseVersion@",
    function(i) {
      if (i > 0) {
        return i + " issues are known to affect Gradle @baseVersion@.";
      } else {
        return "There are no known issues of Gradle @baseVersion@ at this time.";
      }
    }
  );
});

