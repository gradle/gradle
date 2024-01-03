function postProcessCodeBlocks() {
  // Assumptions:
  //  1) All siblings that are marked with class="multi-language-sample" should be grouped
  //  2) Only one language can be selected per domain (to allow selection to persist across all docs pages)
  //  3) There is exactly 1 small set of languages to choose from. This does not allow for multiple language preferences. For example, users cannot prefer both Kotlin and ZSH.
  //  4) Only 1 sample of each language can exist in the same collection.

  var GRADLE_DSLs = ["groovy", "kotlin"];
  var preferredBuildScriptLanguage = initPreferredBuildScriptLanguage();

  // Ensure preferred DSL is valid, defaulting to Kotlin DSL
  function initPreferredBuildScriptLanguage() {
    var lang = window.localStorage.getItem("preferred-gradle-dsl");
    if (GRADLE_DSLs.indexOf(lang) === -1) {
      window.localStorage.setItem("preferred-gradle-dsl", "kotlin");
      lang = "kotlin";
    }
    return lang;
  }

  function capitalizeFirstLetter(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
  }

  function processSampleEl(sampleEl, prefLangId) {
    var codeEl = sampleEl.querySelector("code[data-lang]");
    if (codeEl != null) {
      sampleEl.setAttribute("data-lang", codeEl.getAttribute("data-lang"));
      if (codeEl.getAttribute("data-lang") !== prefLangId) {
        sampleEl.classList.add("hidden");
      } else {
        sampleEl.classList.remove("hidden");
      }
    }
  }

  function switchSampleLanguage(languageId) {
    var multiLanguageSampleElements = [].slice.call(
      document.querySelectorAll(".multi-language-sample")
    );

    // Array of Arrays, each top-level array representing a single collection of samples
    var multiLanguageSets = [];
    for (var i = 0; i < multiLanguageSampleElements.length; i++) {
      var currentCollection = [multiLanguageSampleElements[i]];
      var currentSampleElement = multiLanguageSampleElements[i];
      processSampleEl(currentSampleElement, languageId);
      while (
        currentSampleElement.nextElementSibling != null &&
        currentSampleElement.nextElementSibling.classList.contains(
          "multi-language-sample"
        )
      ) {
        currentCollection.push(currentSampleElement.nextElementSibling);
        currentSampleElement = currentSampleElement.nextElementSibling;
        processSampleEl(currentSampleElement, languageId);
        i++;
      }

      multiLanguageSets.push(currentCollection);
    }

    multiLanguageSets.forEach(function (sampleCollection) {
      // Create selector element if not existing
      if (
        sampleCollection.length > 1 &&
        (sampleCollection[0].previousElementSibling == null ||
          !sampleCollection[0].previousElementSibling.classList.contains(
            "multi-language-selector"
          ))
      ) {
        var languageSelectorFragment = document.createDocumentFragment();
        var multiLanguageSelectorElement = document.createElement("div");
        multiLanguageSelectorElement.classList.add("multi-language-selector");
        languageSelectorFragment.appendChild(multiLanguageSelectorElement);

        sampleCollection.forEach(function (sampleEl) {
          var optionEl = document.createElement("code");
          var sampleLanguage = sampleEl.getAttribute("data-lang");
          optionEl.setAttribute("data-lang", sampleLanguage);
          optionEl.setAttribute("role", "button");
          optionEl.classList.add("language-option");

          optionEl.innerText = capitalizeFirstLetter(sampleLanguage);

          optionEl.addEventListener(
            "click",
            function updatePreferredLanguage(evt) {
              var preferredLanguageId = optionEl.getAttribute("data-lang");
              window.localStorage.setItem(
                "preferred-gradle-dsl",
                preferredLanguageId
              );

              // Record how far down the page the clicked element is before switching all samples
              var beforeOffset = evt.target.offsetTop;

              switchSampleLanguage(preferredLanguageId);

              // Scroll the window to account for content height differences between different sample languages
              window.scrollBy(0, evt.target.offsetTop - beforeOffset);
            }
          );
          multiLanguageSelectorElement.appendChild(optionEl);
        });
        sampleCollection[0].parentNode.insertBefore(
          languageSelectorFragment,
          sampleCollection[0]
        );
      }
    });

    [].slice
      .call(
        document.querySelectorAll(".multi-language-selector .language-option")
      )
      .forEach(function (optionEl) {
        if (optionEl.getAttribute("data-lang") === languageId) {
          optionEl.classList.add("selected");
        } else {
          optionEl.classList.remove("selected");
        }
      });

    [].slice
      .call(document.querySelectorAll(".multi-language-text"))
      .forEach(function (el) {
        if (!el.classList.contains("lang-" + languageId)) {
          el.classList.add("hidden");
        } else {
          el.classList.remove("hidden");
        }
      });
  }

  switchSampleLanguage(preferredBuildScriptLanguage);
}

document.addEventListener("DOMContentLoaded", function () {
  postProcessCodeBlocks();
});
