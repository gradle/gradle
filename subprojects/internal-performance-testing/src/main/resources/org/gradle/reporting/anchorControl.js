$(function enableAllTooltips() {
    $('[data-toggle="tooltip"]').tooltip();
    $('.data-row').mouseenter(function() {
        $('#section-sign-' + $(this).attr('scenario')).show();
    }).mouseleave(function() {
        $('#section-sign-' + $(this).attr('scenario')).hide();
    })

    $('.section-sign').click(function() {
        var $temp = $("<input>");
        $("body").append($temp);
        $temp.val(window.location).select();
        document.execCommand("copy");
        $temp.remove();
    })
})
