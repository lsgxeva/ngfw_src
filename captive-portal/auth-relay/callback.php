<?php

$redirect = "";

if (isset($_REQUEST['code'])) {
    $code = $_REQUEST['code'];
} else {
    echo "<html>Missing code</html>";
    die();
}
if (isset($_REQUEST['state'])) {
    $state = $_REQUEST['state'];
} else {
    echo "<html>Missing state</html>";
    die();
}

$redirect = urldecode($state) . "&authcode=$code";

#show_debug();

$logfile = fopen("/tmp/callback.log","a");
fprintf($logfile,"%s\n","--------------------");
fprintf($logfile,"REDIRECT: %s\n",$redirect);
fprintf($logfile,"STATE: %s\n",$state);
fprintf($logfile,"CODE: %s\n",$code);
fclose($logfile);

echo "<html><head><meta http-equiv=\"Refresh\" CONTENT=\"0; URL=$redirect\">";

// ----------------------------------------------------------------------
function show_debug()
{
Print("<BR><HR><TT>\r\n");

Print("<B>GET</B><P>");
var_dump($_GET);

Print("</P><HR>\r\n");

Print("<B>POST</B><P>");
var_dump($_POST);

Print("</P></TT><HR>\r\n");
}
// ----------------------------------------------------------------------
