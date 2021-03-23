package org.wensheng.wsmcpi;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.enums.Opcode;
import org.java_websocket.framing.PongFrame;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class WSServer extends WebSocketServer {
	private Map<WebSocket, RemoteSession> handlers;
    private WSMCPI plugin;

	WSServer(WSMCPI plugin, int port) {
		super( new InetSocketAddress( port ) );
        this.plugin = plugin;
		plugin.logger.info("Websocket server on "+port);
		handlers = new HashMap<>();
	}

	@Override
	public void onStart() {
		//setConnectionLostTimeout(0);
		//setConnectionLostTimeout(100);
	}

    Map<WebSocket, RemoteSession> getHandlers(){
        return handlers;
    }

	@Override
	public void onOpen(final WebSocket conn, ClientHandshake handshake ) {
		plugin.logger.info("websocket connect from "+conn.getRemoteSocketAddress().getHostName());
		try {
            handlers.put(conn, new RemoteSession(plugin, conn));
		} catch (IOException e) {
            plugin.logger.warning("Could not create remote session");
		}
	}

	@Override
	public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
		plugin.logger.info("websocket closed for reason "+reason);
		RemoteSession session = handlers.get(conn);
		if (session != null) {
			session.close();
			handlers.remove(conn);
		}
	}

	@Override
	public void onMessage( WebSocket conn, String message ) {
		plugin.logger.info("WS got message: " + message);
		if(message.equals("pong")){
			//this server will periodically send ping to client
			return;
		}
		if(message.equals("ping")){
			PongFrame pf = new PongFrame();
			conn.sendFrame(pf);
			return;
		}
		RemoteSession session = handlers.get(conn);
		if (session != null) {
			session.handleLine(message);
		}
	}

	@Override
	public void onMessage( WebSocket conn, ByteBuffer message ) {
		plugin.logger.info("received ByteBuffer from "	+ message.toString());
	}

	@Override
	public void onError( WebSocket conn, Exception ex ) {
	}
	
	@Override
	public void stop() throws IOException, InterruptedException {
		super.stop();
	}
}
