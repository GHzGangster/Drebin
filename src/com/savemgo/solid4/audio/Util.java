package com.savemgo.solid4.audio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class Util {
	
	public static void writeString(String str, ByteBuf buffer) {
		buffer.writeBytes(str.getBytes()).writeZero(1);
	}
	
	public static String readString(ByteBuf buffer) {
		StringBuilder sb = new StringBuilder();
		char c;
		while ((c = (char) buffer.readByte()) != '\0') {
			sb.append(c);
		}
		return sb.toString();
	}
	
	public static void alignReader(int bound, ByteBuf buffer) {
		int readerIndex = buffer.readerIndex();
		int remainder = readerIndex % bound;
		if (remainder != 0) {
			buffer.skipBytes(bound - remainder);
		}
	}
	
	public static void alignWriter(int bound, ByteBuf buffer) {
		int writerIndex = buffer.writerIndex();
		int remainder = writerIndex % bound;
		if (remainder != 0) {
			buffer.writeZero(bound - remainder);
		}
	}
	
	public static ByteBuf readFile(File file) throws Exception {
		ByteBuf bb = null;
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
			FileChannel fc = raf.getChannel();
			bb = UnpooledByteBufAllocator.DEFAULT.directBuffer((int) file.length());
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			while (fc.read(buffer) > 0) {
				buffer.flip();
				bb.writeBytes(buffer);
				buffer.clear();
			}
		} catch (Exception e) {
			safeRelease(bb);
			throw e;
		} finally {
			safeClose(raf);
		}
		return bb;
	}
	
	public static void writeFile(File file, ByteBuf bb) throws Exception {
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "rw");
			FileChannel fc = raf.getChannel();
			int bytesWritten = fc.write(bb.nioBuffer());
			fc.truncate(bytesWritten);
		} catch (Exception e) {
			safeRelease(bb);
			throw e;
		} finally {
			safeClose(raf);
		}
	}
	
	public static void safeRelease(ByteBuf buffer) {
		if (buffer != null && buffer.refCnt() > 0) {
			buffer.release(buffer.refCnt());
		}
	}
	
	public static void safeClose(RandomAccessFile file) {
		if (file != null) {
			try {
				file.close();
			} catch (Exception e) {
			}
		}
	}
	
	public static boolean checkFileInput(File file) {
		return file.isFile() && file.exists() && file.canRead();
	}
	
	public static boolean checkFileOutput(File file) {
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				return false;
			}
		}
		return file.isFile() && file.exists() && file.canWrite();
	}
	
	public static short getShort(byte[] arr, int offset, boolean isBigEndian) {
		if (isBigEndian) {
			return (short) (((arr[offset] & 0xff) << 8) | (arr[offset + 1] & 0xff));
		} else {
			return (short) (((arr[offset] & 0xff + 1) << 8) | (arr[offset] & 0xff));
		}
	}
	
}
