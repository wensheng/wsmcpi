package org.wensheng.wsmcpi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class WSServer extends WebSocketServer {
	Map<WebSocket, RemoteSession> handlers;
    private WSMCPI plugin;
	private static final Logger LOGGER = LogManager.getLogger();
	
	public WSServer(WSMCPI plugin, int port ) throws UnknownHostException {
		super( new InetSocketAddress( port ) );
		System.out.println("Websocket server on "+port);
        this.plugin = plugin;
		handlers = new HashMap<WebSocket, RemoteSession>();
	}

	@Override
	public void onStart() {
		//setConnectionLostTimeout(0);
		//setConnectionLostTimeout(100);
	}

	@Override
	public void onOpen(final WebSocket conn, ClientHandshake handshake ) {
		System.out.println("websocket connect from "+conn.getRemoteSocketAddress().getHostName());
		Writer writer = new Writer() {
			@Override
			public void close() throws IOException {
			}

			@Override
			public void flush() throws IOException {
			}

			@Override
			public void write(char[] data, int start, int len)
					throws IOException {
				conn.send(new String(data, start, len));
			}
		};
		PrintWriter pw = new PrintWriter(writer);
		try {
            handlers.put(conn, new RemoteSession(plugin, conn));
		} catch (IOException e) {
            LOGGER.warn("Could not create remote session");
		}
	}

	@Override
	public void onClose( WebSocket conn, int code, String reason, boolean remote ) {
		System.out.println("websocket closed for reason "+reason);
		RemoteSession session = handlers.get(conn);
		if (session != null) {
			session.close();
			handlers.remove(conn);
		}
	}

	@Override
	public void onMessage( WebSocket conn, String message ) {
		LOGGER.info("WS got message: " + message);
		if(message.equals("ping")){
			conn.send("pong");
			return;
		}
		RemoteSession session = handlers.get(conn);
		if (session != null) {
			session.handleLine(message);
		}
	}


	@Override
	public void onError( WebSocket conn, Exception ex ) {
	}
	
	@Override
	public void stop() throws IOException, InterruptedException {
		super.stop();
	}
}
