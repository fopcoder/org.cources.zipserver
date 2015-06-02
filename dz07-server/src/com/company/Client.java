package com.company;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Client extends Thread {
	private Socket socket;
	private List<Client> clients;
	private FileManager fm;
	private String path;
	private String arch;

	public Client( Socket socket, List<Client> clients, String path ) {
		this.socket = socket;
		this.clients = clients;
		this.path = path;
		// arch dir. better to use thread_id or smtn like that
		this.arch = path + "\\arch";

		fm = new FileManager( path );
	}

	private void return404( OutputStream os ) throws IOException {
		os.write( "HTTP/1.1 404 Not Found\r\n\r\n404 Page Not Found".getBytes() );
		os.flush();
		os.close();
	}

	private byte[] getBinaryHeaders( List<String> headers ) {
		StringBuilder res = new StringBuilder();

		for( String s : headers )
			res.append( s );

		res.append( "\r\n" );

		return res.toString().getBytes();
	}

	public void doGet( OutputStream os, String url ) throws IOException {
		if( "/".equals( url ) ) url = "/index.html";

		byte[] content = fm.get( url );

		if( content == null ) {
			return404( os );
			return;
		}

		List<String> headers = new ArrayList<String>();
		headers.add( "HTTP/1.1 200 OK\r\n" );
		headers.add( "Content-type: text/html;charset=utf-8\r\n" );

		ProcessorsList pl = new ProcessorsList();
		pl.add( new Compressor( 9 ) );
		pl.add( new Chunker( 30 ) ); // comment
		content = pl.process( content, headers );

		if( content != null ) {
			// uncomment next line
			// headers.add("Content-Length: " + content.length + "\r\n");
			headers.add( "Connection: close\r\n" );

			os.write( getBinaryHeaders( headers ) );
			os.write( content );
			os.flush();
			os.close();
		}
		else {
			return404( os );
			return;
		}
	}

	@Override
	public void run() {
		int arrayInc = 100_000;

		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			int r, offset = 0;
			byte[] inData = new byte[arrayInc];
			String url = "", boundary = "", filename = "";

			boolean isPost = false;
			boolean parseHeaders = true;
			int marker = 0; // cursor of new line to read
			int bigData = 0;

			do {
				// expand array if need
				if( inData.length == offset ) {
					inData = Arrays.copyOf( inData, inData.length + arrayInc );
				}

				r = is.read( inData, offset, inData.length - offset );

				if( r > 0 ) {
					offset += r;

					// read headers
					if( marker == 0 ) {
						for( int i = 0; !isRNRN( inData, i ); i++ ) {
							if( isRN( inData, i ) ) { // \r\n
								byte[] t = Arrays.copyOfRange( inData, marker, i - 1 );

								String str = new String( t );

								if( str.startsWith( "GET" ) ) {
									String[] header = str.split( " " );
									url = header[1];
									break; // we don't need other headers
								}
								else if( str.startsWith( "POST" ) ) {
									isPost = true;
								}
								else if( str.contains( "Content-Type: multipart/form-data" ) ) {
									boundary = str.split( "boundary=" )[1];
								}

								marker = i + 1;
							}
						}
						marker += 2;
					}

					if( isPost ) {
						int last = marker;

						for( int i = marker; i < inData.length; i++ ) {
							if( isRN( inData, i ) ) {
								byte[] t = Arrays.copyOfRange( inData, last, i - 1 );

								String str = new String( t );

								// first boundary
								if( str.contains( boundary ) && parseHeaders ) {
									marker = i + 1;
								}
								else if( str.contains( "Content-Disposition" ) && parseHeaders ) {
									filename = str.split( "filename=" )[1].replaceAll( "\"", "" );
									marker = i + 1;
								}
								else if( str.contains( "Content-Type" ) && parseHeaders ) {
									marker = i + 1;
								}
								// start file data
								else if( isRNRN( inData, i ) && parseHeaders ) {
									// i'st important 'cos of \r\n in file data
									parseHeaders = false;
									bigData = i + 1; // start file data
									marker = i + 1;
								}
								// closing boundary
								else if( str.contains( boundary ) && !parseHeaders && bigData > 0 ) {
									byte[] fb = Arrays.copyOfRange( inData, bigData, i - str.length() );

									if( fb.length > 10 ) {
										FileOutputStream fos = new FileOutputStream( path + "\\arch\\" + filename );
										fos.write( fb );
										fos.close();
									}

									filename = "";
									parseHeaders = true;
									bigData = 0;
									marker = i + 1;
								}
								// last boundary. finish
								if( str.contains( boundary + "--" ) ) {
									System.out.println( "=== END ====" );

									List<String> headers = new ArrayList<>();

									File directoryToZip = new File( arch );
									File zipFile = new File( arch + ".zip" );
									ArrayList<File> fileList = new ArrayList<>();
									//
									ZipDirectory.getAllFiles( directoryToZip, fileList );

									byte[] out;

									if( fileList.size() > 0 ) {
										zipFile.delete();
										ZipDirectory.writeZipFile( directoryToZip, fileList );

										headers.add( "HTTP/1.1 200 OK\r\n" );
										headers.add( "Content-Type: application/zip\r\n" );
										headers.add( "Content-Disposition: attachment; filename='arch.zip'\r\n" );
										headers.add( "Content-Transfer-Encoding: binary\r\n" );
										headers.add( "Content-Length: " + ( zipFile.length() ) + "\r\n" );

										out = new byte[(int)zipFile.length()];

										FileInputStream fis = new FileInputStream( zipFile );
										fis.read( out );
										fis.close();
									}
									else {
										headers.add( "HTTP/1.1 200 OK\r\n" );
										headers.add( "Content-Type: text/html\r\n" );

										out = "No files".getBytes();
									}

									os.write( getBinaryHeaders( headers ) );
									os.write( out );
									os.close();

									// clear
									File ad = new File( arch );
									HTTPServer.deleteDirectory( ad );
									ad.mkdirs();

									interrupt();
									break;
								}

								last = i + 1;
							}
						}
					}
					else {
						doGet( os, url );
						interrupt();
						break;
					}

				}
			}
			while( !isInterrupted() );

			clients.remove( this );
		}
		catch( Exception ex ) {
			return;
		}
	}

	public boolean isRN( byte[] b, int idx ) {
		if( idx > 0 && b[idx - 1] == (byte)13 && b[idx] == (byte)10 ) {
			return true;
		}
		return false;
	}

	public boolean isRNRN( byte[] b, int idx ) {
		if( idx > 3 && b[idx - 3] == (byte)13 && b[idx - 2] == (byte)10 && b[idx - 1] == (byte)13 && b[idx] == (byte)10 ) {
			return true;
		}
		return false;
	}

}