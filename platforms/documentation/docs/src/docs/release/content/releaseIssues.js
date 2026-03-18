document.addEventListener('DOMContentLoaded', function() {
  function injectIssues(url, insertAfter, idBase, loadingText, messageFunction) {
    var loadingPara = document.createElement('p');
    loadingPara.className = idBase + '-loading';
    loadingPara.textContent = loadingText + ' …';
    insertAfter.insertAdjacentElement('afterend', loadingPara);

    var animate = true;
    var paraFadeOut = function() {
      if (!animate) return;
      loadingPara.style.transition = 'opacity 0.08s';
      loadingPara.style.opacity = '0';
      setTimeout(function() {
        if (animate) paraFadeIn();
      }, 80);
    };
    var paraFadeIn = function() {
      if (!animate) return;
      loadingPara.style.transition = 'opacity 0.08s';
      loadingPara.style.opacity = '1';
      setTimeout(function() {
        if (animate) paraFadeOut();
      }, 80);
    };
    var finishAnimation = function() {
      animate = false;
      if (loadingPara.parentNode) {
        loadingPara.parentNode.removeChild(loadingPara);
      }
    };
    paraFadeOut();

    fetch(url)
      .then(function(response) {
        if (!response.ok) {
          throw new Error('Network response was not ok');
        }
        return response.json();
      })
      .then(function(data) {
        finishAnimation();
        var para = document.createElement('p');
        para.textContent = messageFunction(data.length);
        insertAfter.insertAdjacentElement('afterend', para);

        if (data.length > 0) {
          var list = document.createElement('ul');
          list.id = idBase + '-list';
          list.style.display = 'none';
          list.style.overflow = 'hidden';
          list.style.transition = 'height 0.5s ease-out';
          para.insertAdjacentElement('afterend', list);

          data.forEach(function(issue) {
            var li = document.createElement('li');
            var link = document.createElement('a');
            link.href = issue.link;
            link.textContent = issue.key;

            li.appendChild(document.createTextNode('['));
            li.appendChild(link);
            li.appendChild(document.createTextNode('] - ' + issue.summary));
            list.appendChild(li);
          });

          // slideDown implementation
          list.style.display = 'block';
          var height = list.scrollHeight + 'px';
          list.style.height = '0px';
          // Trigger reflow
          list.offsetHeight;
          list.style.height = height;
          setTimeout(function() {
            list.style.height = '';
            list.style.overflow = '';
            list.style.transition = '';
          }, 500);
        }
      })
      .catch(function() {
        finishAnimation();
        var errorPara = document.createElement('p');
        errorPara.textContent = 'Unable to retrieve the issue information. You may not be connected to the Internet, or there may have been an error.';
        errorPara.style.fontWeight = 'bold';
        errorPara.style.color = 'red';
        insertAfter.insertAdjacentElement('afterend', errorPara);
      });
  }

  var fixedIssuesHeader = document.getElementById('fixed-issues');
  if (fixedIssuesHeader) {
    injectIssues(
      "https://services.gradle.org/fixed-issues/@baseVersion@",
      fixedIssuesHeader.parentElement,
      "fixed-issues",
      "Retrieving the fixed issue information for @baseVersion@",
      function(i) {
        return i + " issues have been fixed in Gradle @baseVersion@.";
      }
    );
  }

  var knownIssuesHeader = document.getElementById('known-issues');
  if (knownIssuesHeader) {
    var knownIssuesParent = knownIssuesHeader.parentElement;
    var insertAfter = knownIssuesParent.nextElementSibling;
    injectIssues(
      "https://services.gradle.org/known-issues/@baseVersion@",
      insertAfter || knownIssuesParent,
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
  }
});
