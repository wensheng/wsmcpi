var mc = new Minecraft();

function flatten(){
  var x = parseInt($("#px").text()),
      y = parseInt($("#py").text()),
      z = parseInt($("#pz").text());
  if(!isNaN(x) && !isNaN(y) && !isNaN(z)){
    mc.send(`world.setBlocks(${x-40},${y-1},${z-40},${x+40},${y-1},${z+40},sandstone)`);
    mc.send(`world.setBlocks(${x-40},${y},${z-40},${x+40},${y+40},${z+40},air)`);
  }
}

function show_message(message){
  $('#message').text(message).show();
  setTimeout(function(){
    $('#message').fadeOut(3000);
  }, 3000);
}

$('#connect').on('click', function(){
  $(this).attr("disabled", true);
  mc.connect("192.168.2.116", 4721);
});

function getXYZ(){
  var x = parseInt($("#pos-x").val());
  var y = parseInt($("#pos-y").val());
  var z = parseInt($("#pos-z").val());
  if(isNaN(x) || isNaN(y) || isNaN(z)){
    show_message("invalid x, y, z values");
    return [null, null, null];
  }
  return [x, y, z];
}


$('#teleport-btn').on('click', function(){
  var x, y, z;
  [x, y, z] = getXYZ();
  if(x == null) return;
  mc.send(`player.setPos(${x},${y},${z})`); 
});

$('#setblock-btn').on('click', function(){
  var x, y, z;
  [x, y, z] = getXYZ();
  if(x == null) return;
  var itemType = $('#setblock-type').val();
  mc.send(`world.setBlock(${x},${y},${z},${itemType})`); 
});

$('#spawn-btn').on('click', function(){
  var x, y, z;
  [x, y, z] = getXYZ();
  if(x == null) return;
  var itemType = $('#spawn-type').val();
  mc.send(`world.spawnEntity(${x},${y},${z},${itemType})`); 
});

$('#setblocks-btn').on('click', function(){
  var x1 = parseInt($("#setblocks-x1").val());
  var y1 = parseInt($("#setblocks-y1").val());
  var z1 = parseInt($("#setblocks-z1").val());
  var x2 = parseInt($("#setblocks-x2").val());
  var y2 = parseInt($("#setblocks-y2").val());
  var z2 = parseInt($("#setblocks-z2").val());
  if(isNaN(x1) || isNaN(y1) || isNaN(z1) || isNaN(x2) || isNaN(y2) || isNaN(z2)){
    show_message("invalid x, y, z values");
    return;
  }
  var itemType = $('#setblocks-type').val();
  mc.send(`world.setBlocks(${x1},${y1},${z1},${x2},${y2},${z2},${itemType})`); 
});

$('#flatten-btn').on('click', function(){
  flatten();
});


mc.on("data", function(){
  var parts = mc.msgQ.pop().split(',');
  if(parts.length == 3){
    //seems like a position
    $("#px").text(parseInt(parts[0]));
    $("#py").text(parseInt(parts[1]));
    $("#pz").text(parseInt(parts[2]));
  }
});

/* get player position every 10 seconds */
setInterval(function(){
  if(mc.ws != null && mc.ws.readyState < 2){
    mc.send("player.getPos()");
  }
}, 10000);
