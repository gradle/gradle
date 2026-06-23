/* Custom JS on top of external clipboard.js */
window.onload = function() {
    var pre = document.getElementsByTagName('pre');

    for (var i = 0; i < pre.length; i++) {
        var b = document.createElement('button');
        b.className = 'clipboard';
        b.textContent = ' ';
        if (pre[i].childNodes.length === 1 && pre[i].childNodes[0].nodeType === 3) {
            var div = document.createElement('div');
            div.textContent = pre[i].textContent;
            pre[i].textContent = '';
            pre[i].appendChild(div);
        }
        pre[i].appendChild(b);
    }

    var clipboard = new ClipboardJS('.clipboard', {
       text: function(b) {
            var p = b.parentNode;
            var sourceEl;
            if (p.className.includes("highlight")) {
                var elems = p.getElementsByTagName("code");
                sourceEl = elems.length > 0 ? elems[0] : p.childNodes[0];
            } else {
                sourceEl = p.childNodes[0];
            }
            var text = sourceEl.textContent;
            return text.replace(/^(\$ )/gm, '');
        }
    });

    clipboard.on('success', function(e) {
        e.clearSelection();
        e.trigger.classList.add('clipboard_success');
        setTimeout(function() {
            e.trigger.classList.remove('clipboard_success');
        }, 1300);
    });

    clipboard.on('error', function(e) {
        console.error('Action:', e.action, e);
        console.error('Trigger:', e.trigger);
    });
};
