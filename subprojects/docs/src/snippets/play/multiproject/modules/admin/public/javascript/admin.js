function displayAdminMessage() {
    document.getElementById("adminMessage").innerHTML = "Loaded admin module javascript at " + new Date();
}
window.onload=displayAdminMessage;
