package com.company;

public class CacheRecord {
	private byte[] data;
	private long time;

	public CacheRecord( byte[] data ) {
		this.data = data;
		this.time = System.currentTimeMillis();
	}

	public byte[] getData() {
		return data;
	}

	public void setData( byte[] data ) {
		this.data = data;
	}

	public long getTime() {
		return time;
	}

	public void setTime( long time ) {
		this.time = time;
	}
}
