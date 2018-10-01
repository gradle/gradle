$(function enableAllTooltips() {
    $('[data-toggle="tooltip"]').tooltip();
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

        $('.card').each(function (index, row) {
            var currentTags = $(row).attr('tag');
            if (currentTags === undefined) {
                currentTags = ''
            }
            currentTags = currentTags.split(',');
            if (selectedTags.some(tag => currentTags.indexOf(tag) != -1)) {
                $(row).show()
            } else {
                $(row).hide()
            }
        })
    })

})
