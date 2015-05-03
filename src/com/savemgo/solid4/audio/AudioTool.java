package com.savemgo.solid4.audio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.io.File;

public class AudioTool {
	
	public AudioTool() throws Exception {
		// File fin = new File("working-mta2/Bullet-Dance.raw");
		// File fout = new File("working-mta2/Bullet-Dance-encoded.bin");
		//
		// ByteBuf bb = Util.readFile(fin);
		//
		// ByteBuf sb = UnpooledByteBufAllocator.DEFAULT.directBuffer(0x1000 *
		// 0x90);
		// int loops = 0x1000;
		// for (int i = 0; i < loops; i++) {
		// audioEncode(bb, sb, false);
		// if ((i + 1) % 500 == 0) {
		// System.out.printf("%d%%\n", ((i + 1) * 100 / loops));
		// }
		// }
		// System.out.println("Done.");
		
		// File fin = new File("working-mta2/Bullet-Dance-encoded.bin");
		// File fout = new File("working-mta2/Bullet-Dance-decoded.bin");
		//
		// ByteBuf bb = Util.readFile(fin);
		//
		// ByteBuf sb = UnpooledByteBufAllocator.DEFAULT.directBuffer(0x1000 *
		// 0x200);
		// int loops = 0x1000;
		// for (int i = 0; i < loops; i++) {
		// audioDecode(bb, sb);
		// if ((i + 1) % 500 == 0) {
		// System.out.printf("%d%%\n", ((i + 1) * 100 / loops));
		// }
		// }
		// System.out.println("Done.");
		//
		// Util.writeFile(fout, sb);
		// sb.release();
		// bb.release();
		
		 File fin = new File("working-mta2/file0.mta2");
		 ByteBuf bb = Util.readFile(fin);
		 MTA2 mta2 = new MTA2(bb);
		 bb.release();
	}
	
	private static final int c1[] = { 0, 240, 460, 392, 488, 460, 460, 240 };
	private static final int c2[] = { 0, 0, -208, -220, -240, -240, -220, -104 };
	private static final int c3[] = { 256, 335, 438, 573, 749, 979, 1281, 1675, 2190, 2864, 3746,
		4898, 6406, 8377, 10955, 14327, 18736, 24503, 32043, 41905, 54802, 71668, 93724, 122568,
		160290, 209620, 274133, 358500, 468831, 613119, 801811, 1048576 };
	
	public static short calculateOutput(int nibble, short smp1, short smp2, int mod, int sh) {
		if (nibble > 7) {
			nibble = nibble - 16;
		}
		int output = (smp1 * c1[mod] + smp2 * c2[mod] + (nibble * c3[sh]) + 128) >> 8;
		if (output > 32767) {
			output = 32767;
		} else if (output < -32768) {
			output = -32768;
		}
		return (short) output;
	}
	
	/**
	 * Decodes audio.
	 * 
	 * @param in
	 * @param out
	 */
	public static void decodeBlock(ByteBuf in, ByteBuf out) {
		int readerOff = in.readerIndex();
		System.out.printf("Reader Index: %x\n", readerOff);
		
		short headerSamples[] = new short[8];
		int mod[] = new int[4];
		int sh[] = new int[4];
		
		for (int group = 0; group < 4; group++) {
			int sampleHeader = in.readInt();
			headerSamples[group * 2] = (short) ((sampleHeader >> 16) & 0xfff0);
			headerSamples[group * 2 + 1] = (short) ((sampleHeader >> 4) & 0xfff0);
			mod[group] = (sampleHeader >> 5) & 0x7;
			sh[group] = sampleHeader & 0x1f;
		}
		
		byte[] data = new byte[0x80];
		in.readBytes(data);
		
		for (int group = 0; group < 4; group++) {
			short smp2 = headerSamples[group * 2];
			short smp1 = headerSamples[group * 2 + 1];
			out.writeShort(smp2);
			out.writeShort(smp1);
			
			for (int row = 0; row < 8; row++) {
				for (int col = 0; col < 4; col++) {
					byte nibbles = data[group * 4 + row * 0x10 + col];
					
					short output = calculateOutput(nibbles >> 4, smp1, smp2, mod[group], sh[group]);
					if (row < 7 || col < 3) {
						out.writeShort(output);
					}
					smp2 = smp1;
					smp1 = output;
					
					output = calculateOutput(nibbles & 0xf, smp1, smp2, mod[group], sh[group]);
					if (row < 7 || col < 3) {
						out.writeShort(output);
					}
					smp2 = smp1;
					smp1 = output;
				}
			}
		}
		
		int readBytes = in.readerIndex() - readerOff;
		System.out.printf("Read %x bytes.\n", readBytes);
	}
	
	/**
	 * Encodes audio through brute force. Determines the output by whichever
	 * parameters give the lowest delta.
	 * 
	 * @param in
	 * @param out
	 */
	public static void encodeBlock(ByteBuf in, ByteBuf out, boolean isBigEndian) {		
		byte[] samples = new byte[0x204];
		int[] headers = new int[4];
		byte[] encoded = new byte[0x80];
		
		int readerIndex = in.readerIndex();
		int readable = in.readableBytes();
		int bytesToRead = Math.min(readable, 0x204);
		in.readBytes(samples, 0, bytesToRead);
		if (bytesToRead == 0x204) {
			in.readerIndex(readerIndex + 0x200);
		} else {
			in.readerIndex(readerIndex + readable);
		}
		
		for (int group = 0; group < 4; group++) {
			byte nibbles[][][] = new byte[32][8][0x40];
			int totalDeltas[][] = new int[32][8];
			int bestSh = 0, bestMod = 0, bestNibble = 0, bestNibbleDelta = 0, bestTotalDelta = 0;
			short bestNibbleOutput = 0;
			
			int samplesOffset = group * 0x80;
			
			short smp2Orig = Util.getShort(samples, samplesOffset, isBigEndian);
			short smp1Orig = Util.getShort(samples, samplesOffset + 2, isBigEndian);
			
			for (int sh = 0; sh < 32; sh++) {
				for (int mod = 0; mod < 8; mod++) {
					
					short smp2 = (short) (smp2Orig & 0xfff0);
					short smp1 = (short) (smp1Orig & 0xfff0);
					
					for (int sampleNum = 0; sampleNum < 0x40; sampleNum++) {
						
						short sample = Util.getShort(samples, samplesOffset + 4 + sampleNum * 2,
							isBigEndian);
						
						for (int nibble = -8; nibble <= 7; nibble++) {
							short output = calculateOutput(nibble, smp1, smp2, mod, sh);
							
							int delta = Math.abs(sample - output);
							if (nibble == -8 || delta < bestNibbleDelta) {
								bestNibbleDelta = delta;
								bestNibble = nibble;
								bestNibbleOutput = output;
							}
						}
						
						nibbles[sh][mod][sampleNum] = (byte) bestNibble;
						totalDeltas[sh][mod] += bestNibbleDelta;
						
						smp2 = smp1;
						smp1 = bestNibbleOutput;
					}
					
					if ((sh == 0 && mod == 0) || totalDeltas[sh][mod] < bestTotalDelta) {
						bestTotalDelta = totalDeltas[sh][mod];
						bestSh = sh;
						bestMod = mod;
					}
					
				}
				
			}
			
			headers[group] = ((smp2Orig & 0xfff0) << 16) | ((smp1Orig & 0xfff0) << 4)
				| ((bestMod & 0x7) << 5) | (bestSh & 0x1f);
			
			for (int row = 0; row < 8; row++) {
				for (int col = 0; col < 4; col++) {
					encoded[(row + 1) * 0x10 + col] = (byte) (((nibbles[bestSh][bestMod][(row * 4 + col) * 2] & 0xf) << 4) | (nibbles[bestSh][bestMod][(row * 4 + col) * 2 + 1] & 0xf));
				}
			}
		}
		
		for (int i = 0; i < 4; i++) {
			out.writeInt(headers[i]);
		}
		out.writeBytes(encoded);
	}
	
	public static void main(String[] args) {
		try {
			new AudioTool();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
