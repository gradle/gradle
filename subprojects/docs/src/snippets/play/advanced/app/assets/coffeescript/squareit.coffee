square = (x) -> x * x

$ ->
    $('#number').val(2)
    $('#squareit').click ->
        console.log("squared!")
        $('#number').val(square($('#number').val()))