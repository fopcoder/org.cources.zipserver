package com.company;

import java.lang.Thread;

public class Main {
	public static void main( String[] args ) {
		final HTTPServer server = new HTTPServer( 8080, "C:\\Temp\\serv" );
		server.start();

		System.out.println( "Server started..." );

		Runtime.getRuntime().addShutdownHook( new Thread() {
			@Override
			public void run() {
				server.stop();
				System.out.println( "Server stopped!" );
			}
		} );
	}
}