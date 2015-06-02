package com.company;

import java.io.File;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class HTTPServer {
	private int port;
	private String path;
	private Thread listenThread;
	private List<Client> clients = new ArrayList<>();
	private String arch = "\\arch";

	public HTTPServer( int port, String path ) {
		this.port = port;
		this.path = path;

		File ad = new File( this.getArchDirectory() );
		deleteDirectory( ad );
		ad.mkdirs();

		File ad1 = new File( this.getArchDirectory() + ".zip" );
		ad1.delete();
	}

	public void start() {

		listenThread = new Thread() {
			@Override
			public void run() {
				try {
					ServerSocket srv = new ServerSocket( port );
					try {
						while( !isInterrupted() ) {
							Client client = new Client( srv.accept(), clients, path );
							clients.add( client );
							client.start();

							Thread.sleep( 50 );
						}
					}
					finally {
						srv.close();
					}
				}
				catch( Exception ex ) {
					return;
				}
			}
		};
		listenThread.start();
	}

	public void stop() {
		listenThread.interrupt();

		for( Client client : clients )
			client.interrupt();
	}

	public void setPath( String path ) {
		this.path = path;
	}

	public String getArchDirectory() {
		return this.path + this.arch;
	}

	public static boolean deleteDirectory( File directory ) {
		if( directory.exists() ) {
			File[] files = directory.listFiles();
			if( null != files ) {
				for( int i = 0; i < files.length; i++ ) {
					if( files[i].isDirectory() ) {
						deleteDirectory( files[i] );
					}
					else {
						files[i].delete();
					}
				}
			}
		}
		return ( directory.delete() );
	}
}