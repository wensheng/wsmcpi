class Minecraft extends EventEmitter {
  ws = null;
  msgQ = [];
  waitCounter = 10;

  connect(host="localhost", port=4721) {
    var self = this;
    this.ws = new WebSocket("ws://" + host + ":" + port);
    this.ws.onopen = function(e){
      console.log("Connected to: " + e.currentTarget.url);
    };
    this.ws.onmessage = function(e){
      self.msgQ.push(e.data);
      var parts = e.data.split(",");
      if(parts.length >= 3){
        self.emit("data");
      }
      console.log("From server: " + e.data);
    };
  }

  send(command) {
    this.ws.send(command);
  }

  close() {
    this.ws.close();
  }
}
