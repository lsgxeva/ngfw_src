// Copyright (c) 2006 Metavize Inc.
// All rights reserved.

var openImg = new Image();
openImg.src = "open.gif";
var closedImg = new Image();
closedImg.src = "closed.gif";

function expandDir(dir)
{
  var dirElem = $(dir);

  if (isFetchable(dirElem)) {
    new Ajax.Request("ls",
                     { method: "get",
                       parameters: "url=" + dir + "&type=dir",
                       onComplete: function(req)
                                   {
                                     var resp = parseDomFromString(req.responseText);
                                     var root = resp.getElementsByTagName('root')[0];
                                     if (isFetchable(dirElem)) {
                                       addChildDirectories(dirElem, root);
                                     }
                                   }
                     });
  } else {
    toggleTree(dir);
  }
}

function showFileListing(dir)
{
  alert("showFileListing: " + dir);
}

function isFetchable(target)
{
    // XXX add leaf node checking
    return 0 == target.childNodes.length;
}

function addChildDirectories(target, dom)
{
  var path = target.getAttribute("id");

  for (var i = 0; i < dom.childNodes.length; i++) {
    if ("dir" == dom.childNodes[i].tagName) {
      var name = dom.childNodes[i].getAttribute("name");
      var childPath = path + name;
      addChildDirectory(target, name, childPath);
    }
  }

  toggleTree(path);
}

function addChildDirectory(target, name, path)
{
  var trig = document.createElement("span");
  Element.addClassName(trig, "trigger");

  var img = document.createElement("img");
  img.setAttribute("src", "closed.gif");
  img.setAttribute("id", "I" + path);
  img.onclick = function() { expandDir(path); };
  trig.appendChild(img);

  var listTrigger = document.createElement("span");
  Element.addClassName(listTrigger, "list-trigger");
  listTrigger.onclick = function() { showFileListing(path); };

  var text = document.createTextNode(name.substring(0, name.length - 1));
  listTrigger.appendChild(text);

  trig.appendChild(listTrigger);

  var br = document.createElement("br");
  trig.appendChild(br);

  var dir = document.createElement("span");
  Element.addClassName(dir, "dir");
  dir.setAttribute("id", path);

  target.appendChild(trig);
  target.appendChild(dir);
}

function toggleTree(dir)
{
  var dirElem = $(dir);
  var dirStyle = dirElem.style;
  var dirImg = $("I" + dir);

  if (dirStyle.display == "block") {
    dirStyle.display = "none";
    dirImg.src = closedImg.src;
  } else {
    dirStyle.display = "block";
    dirImg.src = openImg.src;
  }
}

function parseDomFromString(text)
{
  return Try.these(function()
                   {
                     return new DOMParser().parseFromString(text, 'text/xml');
                   },
                   function()
                   {
                     var xmlDom = new ActiveXObject("Microsoft.XMLDOM")
                     xmlDom.async = "false"
                     xmlDom.loadXML(text)
                     return xmlDom;
                   });
}