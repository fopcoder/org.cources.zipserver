package com.company;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager {
	private String path;
	private long ttl = 10000;

	private static ConcurrentHashMap<String, CacheRecord> map = new ConcurrentHashMap<>();

	public FileManager( String path ) {
		if( path.endsWith( "/" ) || path.endsWith( "\\" ) ) path = path.substring( 0, path.length() - 1 );

		this.path = path;
	}

	public byte[] get( String url ) {
		try {
			if( map.containsKey( url ) && System.currentTimeMillis() - map.get( url ).getTime() < ttl ) {
				System.out.println( "==== cache ====" );
				return map.get( url ).getData();
			}
			else {
				System.out.println( "==== file ====" );
				String fullPath = path + url.replace( '/', '\\' );
				byte[] buf;

				File fp = new File( fullPath );
				if( !fp.exists() ) {
					return null;
				}

				RandomAccessFile f = new RandomAccessFile( fp, "r" );
				try {
					buf = new byte[(int)f.length()];
					f.read( buf, 0, buf.length );
				}
				finally {
					f.close();
				}

				map.put( url, new CacheRecord( buf ) );

				return buf;
			}
		}
		catch( IOException ex ) {
			return null;
		}
	}

}
