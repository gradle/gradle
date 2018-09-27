$(function enableAllTooltips() {
    $('[data-toggle="tooltip"]').tooltip();
    $('.data-row').mouseenter(function() {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '1');
    }).mouseleave(function() {
        $('#section-sign-' + $(this).attr('scenario')).css('opacity', '0');
    });

    $('.section-sign').click(function() {
        var $temp = $("<input>");
        $("body").append($temp);
        $temp.val(window.location.href.split('#')[0] + $(this).attr('href')).select();
        document.execCommand("copy");
        $temp.remove();
    });


    $('[data-toggle="popover"]').popover({
        html : true,
        content: function() {
            return $('#filter-popover').html();
        }
    });


    $(document).on('click', '.form-check-input', function () {
        var selectedTags = $('.popover-body .form-check-input').toArray().filter(function(checkbox) {
            return checkbox.checked
        }).map(function (checkbox) { return checkbox.value })

        $('.card').each(function (index, row) {
            var currentTag = $(row).attr('tag')
            if(currentTag === undefined) {
                currentTag = ''
            }
            if(selectedTags.some(function(tag) { return currentTag.indexOf(tag) != -1 })) {
                $(row).show()
            } else {
                $(row).hide()
            }
        })
    })

})
