function enableTooltips() {
    $('[data-toggle="tooltip"]').tooltip();
}

function enableSectionSign() {
    $('.data-row').mouseenter(function () {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '1');
    }).mouseleave(function () {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '0');
    });

    $('.section-sign').click(function () {
        var $temp = $("<input>");
        $("body").append($temp);
        $temp.val(window.location.href.split('#')[0] + $(this).attr('href')).select();
        document.execCommand("copy");
        $temp.remove();
    });
}

function refreshCards(selectedTags) {
    $('.card').each(function (index, row) {
        var currentTags = $(row).attr('tag');
        if (currentTags === undefined) {
            currentTags = ''
        }
        // tag="FAILURE-THRESHOLD(4.16%),FLAKY(9.30%)"
        // tag="UNTAGGED"
        currentTags = currentTags.split(',').map(tag => tag.split('(')[0])
        if (selectedTags.some(tag => currentTags.indexOf(tag) != -1)) {
            $(row).show()
        } else {
            $(row).hide()
        }
    })

    $("#filter-popover .form-check-input").toArray().forEach(function (checkbox) {
        if (selectedTags.indexOf(checkbox.value) != -1) {
            checkbox.setAttribute('checked', 'true');
        } else {
            checkbox.removeAttribute('checked')
        }
    })

}

function enableFilter() {
    $('[data-toggle="popover"]').popover({
        html: true,
        content: function () {
            return $('#filter-popover').html();
        }
    });

    $(document).on('click', '.form-check-input', function () {
        var currentSelectedTag = $(this).val();
        var checked = $(this).prop('checked');

        $("#filter-popover .form-check-input[value*='" + currentSelectedTag + "']").toArray().forEach(checkbox => checked ? checkbox.setAttribute('checked', 'true') : checkbox.removeAttribute('checked'));

        var selectedTags = $('.popover-body .form-check-input').toArray().filter(checkbox => checkbox.checked).map(checkbox => checkbox.value);

        refreshCards(selectedTags)
    })
}

function failedScenarioButtonClicked() {
    $('[data-toggle="popover"]').popover('hide')
    $('#failed-scenarios').removeClass('btn-outline-danger').addClass('btn-danger')
    $('#all-scenarios').addClass('btn-outline-primary').removeClass('btn-primary')
    refreshCards(['FAILED', 'REGRESSED', 'UNKNOWN'])
}

function allScenarioButtonClicked() {
    $('[data-toggle="popover"]').popover('hide')
    $('#all-scenarios').removeClass('btn-outline-primary').addClass('btn-primary')
    $('#failed-scenarios').addClass('btn-outline-danger').removeClass('btn-danger')

    var allTags = $('#filter-popover .form-check-input').toArray().map(checkbox => checkbox.value);
    refreshCards(allTags)
}

function initTabs() {
    if (window.location.hash.length > 0) {
        allScenarioButtonClicked()
    } else if ($('#failed-scenarios').length > 0) {
        failedScenarioButtonClicked()
    }

    $('#failed-scenarios').click(failedScenarioButtonClicked);

    $('#all-scenarios').click(allScenarioButtonClicked);
}


$(function () {
    enableTooltips();
    enableSectionSign();
    enableFilter();
    initTabs();
})
