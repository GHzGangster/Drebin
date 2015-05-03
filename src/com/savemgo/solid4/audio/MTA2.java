package com.savemgo.solid4.audio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.io.File;

@SuppressWarnings("unused")
public class MTA2 {
	
	private int channels = 0;
	private int tracks = 0;
	private int frames = 0;
	private int samplesPerFrame = 0;
	
	private boolean isLooped = false;
	private int loopStart = 0x0;
	private int loopEnd = 0x0;
	
	public MTA2(ByteBuf inBuf) {
		parseMTA2(inBuf);
	}
	
	public void parseMTA2(ByteBuf bb) {
		boolean sergioFile = true;
		
		int startOffset = 0x0;
		
		// Look for the MTA2 header, detemine the type of file
		if (bb.getByte(0x0) == (byte) 0x4d && bb.getByte(0x1) == (byte) 0x54
			&& bb.getByte(0x2) == (byte) 0x41 && bb.getByte(0x3) == (byte) 0x32) {
			// Plain
			startOffset = 0x0;
		} else if (bb.getByte(0x20) == (byte) 0x4d && bb.getByte(0x21) == (byte) 0x54
			&& bb.getByte(0x22) == (byte) 0x41 && bb.getByte(0x23) == (byte) 0x32) {
			// BGM
			startOffset = 0x20;
		} else {
			System.err.println("Unknown MTA2 type.");
			return;
		}
		
		int unk1 = bb.getInt(startOffset + 0x4); // Related to total size?
		
		int channelsTimesTracks = bb.getShort(startOffset + 0x56);
		
		// Dividing by 0x100 gets us the frame, but sometimes there is a
		// remainder
		loopStart = bb.getInt(startOffset + 0x58) / 0x100;
		loopEnd = bb.getInt(startOffset + 0x5c) / 0x100;
		
		frames = loopEnd;
		
		isLooped = (bb.getByte(startOffset + 0x73) == (byte) 0x01);
		
		// Loop through all the TRKP sections, all we're really doing is
		// counting the number of tracks
		byte channelsIdentifier = 0x00;
		for (int i = 0; i < 16; i++) {
			channelsIdentifier = bb.getByte(0xf8 + i * 0x70 + 0xf);
			
			if (channelsIdentifier == 0) {
				break;
			}
			
			tracks++;
			
			if (channels == 0) {
				if (channelsIdentifier == (byte) 0x04) {
					channels = 1;
				} else if (channelsIdentifier == (byte) 0x03) {
					channels = 2;
				} else if (channelsIdentifier == (byte) 0x07) {
					channels = 3;
				} else if (channelsIdentifier == (byte) 0x33) {
					channels = 4;
					// 37 = 5 channels?
				} else if (channelsIdentifier == (byte) 0x3f) {
					channels = 6;
				}
			}
		}
		
		tracks = 1;
		System.out.printf("Channels: %d Tracks: %d Sergio File? %b\n", channels, tracks, sergioFile);
		
		ByteBuf sb = UnpooledByteBufAllocator.DEFAULT.directBuffer(frames * 0x90 * 2 * 1);
		
		if (sergioFile) {
			bb.readerIndex(0x800);
		} else {
			bb.readerIndex(0x1000);
		}
		
		int section = 0;
		int blockLength = 0;
		byte channelsIdentifierFrame = 0;
		int elementsInBlock = 0;
		
		int trackSampleNum = 0, trackNum = 0, sampleNum = 0, sampleLength = 0;
		
		int frameOffset = 0;
		
		if (!sergioFile) {
			do {
				section = bb.readInt();
				blockLength = bb.readInt();
				bb.skipBytes(4);
				elementsInBlock = bb.readInt() * tracks;
				
				if (section == 0x10001) {
					if (elementsInBlock != 0) {
						for (int i = 0; i < elementsInBlock; i++) {
							int blockOffset = bb.readerIndex();
							
							trackSampleNum = bb.readInt();
							trackNum = (trackSampleNum >> 24) & 0xff;
							sampleNum = trackSampleNum & 0xffffff;
							bb.skipBytes(1);
							channelsIdentifierFrame = bb.readByte();
							sampleLength = bb.readUnsignedShort();
							bb.skipBytes(8);
							
							frameOffset = bb.readerIndex();
							for (int j = 0; j < channels; j++) {
								if (j == 3 && trackNum == 0) {
									AudioTool.decodeBlock(bb, sb);
								} else {
									bb.skipBytes(0x90);
								}
							}
						}
					} else {
						bb.skipBytes(blockLength - 0x10);
					}
				}
			} while (section != 0xf0);
		} else {
			do {				
				trackSampleNum = bb.readInt();
				trackNum = (trackSampleNum >> 24) & 0xff;
				sampleNum = trackSampleNum & 0xffffff;
				bb.skipBytes(1);
				channelsIdentifierFrame = bb.readByte();
				sampleLength = bb.readUnsignedShort();
				bb.skipBytes(8);
				
				frameOffset = bb.readerIndex();
				for (int j = 0; j < channels; j++) {
					if (j == 0 && trackNum == 0) {
						AudioTool.decodeBlock(bb, sb);
					} else {
						bb.skipBytes(0x90);
					}
				}
			} while (bb.readableBytes() > 0);
		}
		
		try {
			File fout = new File("working-mta2/out.bin");
			Util.writeFile(fout, sb);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void editMTA2(ByteBuf bb) {
		boolean sergioFile = false;
		
		boolean isBigEndian = false;
		File fin = new File("working-mta2/Bullet-Dance.raw");
		ByteBuf sampleBuffer;
		try {
			sampleBuffer = Util.readFile(fin);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		
		int startOffset = 0x0;
		
		// Look for the MTA2 header, detemine the type of file
		if (bb.getByte(0x0) == (byte) 0x4d && bb.getByte(0x1) == (byte) 0x54
			&& bb.getByte(0x2) == (byte) 0x41 && bb.getByte(0x3) == (byte) 0x32) {
			// Plain
			startOffset = 0x0;
		} else if (bb.getByte(0x20) == (byte) 0x4d && bb.getByte(0x21) == (byte) 0x54
			&& bb.getByte(0x22) == (byte) 0x41 && bb.getByte(0x23) == (byte) 0x32) {
			// BGM
			startOffset = 0x20;
		} else {
			System.err.println("Unknown MTA2 type.");
			sampleBuffer.release();
			return;
		}
		
		int totalSize = bb.getInt(startOffset + 0x4);
		
		int channelsTimesTracks = bb.getShort(startOffset + 0x56);
		
		// Dividing by 0x100 gets us the frame, but sometimes there is a
		// remainder
		loopStart = bb.getInt(startOffset + 0x58) / 0x100;
		loopEnd = bb.getInt(startOffset + 0x5c) / 0x100;
		
		frames = loopEnd;
		
		isLooped = (bb.getByte(startOffset + 0x73) == (byte) 0x01);
		
		// Loop through all the TRKP sections, all we're really doing is
		// counting the number of tracks
		byte channelsIdentifier = 0x00;
		for (int i = 0; i < 16; i++) {
			channelsIdentifier = bb.getByte(0x118 + i * 0x70 + 0xf);
			
			if (channelsIdentifier == 0) {
				break;
			}
			
			tracks++;
			
			if (channels == 0) {
				if (channelsIdentifier == (byte) 0x04) {
					channels = 1;
				} else if (channelsIdentifier == (byte) 0x03) {
					channels = 2;
				} else if (channelsIdentifier == (byte) 0x07) {
					channels = 3;
				} else if (channelsIdentifier == (byte) 0x33) {
					channels = 4;
					// 37 = 5 channels?
				} else if (channelsIdentifier == (byte) 0x3f) {
					channels = 6;
				}
			}
		}
		
		tracks = 1;
		System.out.printf("Channels: %d Tracks: %d\n", channels, tracks);
		
		ByteBuf sb = UnpooledByteBufAllocator.DEFAULT.directBuffer(0x90);

		bb.readerIndex(0x1000);
		
		int section = 0;
		int blockLength = 0;
		byte channelsIdentifierFrame = 0;
		int elementsInBlock = 0;
		
		int trackSampleNum = 0, trackNum = 0, sampleNum = 0, sampleLength = 0;
		
		int frameOffset = 0;
		
		do {
			section = bb.readInt();
			blockLength = bb.readInt();
			bb.skipBytes(4);
			elementsInBlock = bb.readInt();
			
			if (section == 0x10001) {
				// Sample data
				if (elementsInBlock != 0) {
					for (int i = 0; i < elementsInBlock; i++) {
						int blockOffset = bb.readerIndex();
						
						trackSampleNum = bb.readInt();
						trackNum = (trackSampleNum >> 24) & 0xff;
						sampleNum = trackSampleNum & 0xffffff;
						bb.skipBytes(1);
						channelsIdentifierFrame = bb.readByte();
						sampleLength = bb.readUnsignedShort();
						bb.skipBytes(8);
						
						frameOffset = bb.readerIndex();
						for (int j = 0; j < channels; j++) {
							if (j == 0 && trackNum == 0) {
								if ((sampleNum + 1) % 500 == 0) {
									System.out.printf("%d/%d\n", sampleNum, frames);
								}
								
								bb.skipBytes(0x90);
								
								AudioTool.encodeBlock(sampleBuffer, sb, isBigEndian);
								bb.setBytes(frameOffset + j * 0x90, sb);
								sb.clear();
							} else {
								bb.skipBytes(0x90);
								bb.setInt(frameOffset + j * 0x90, 0xe0);
								bb.setInt(frameOffset + 4 + j * 0x90, 0xe0);
								bb.setInt(frameOffset + 8 + j * 0x90, 0xe0);
								bb.setInt(frameOffset + 12 + j * 0x90, 0xe0);
								for (int z = 0; z < 0x80; z++) {
									bb.setByte(frameOffset + 16 + z + j * 0x90, 0x00);
								}
							}
						}
					}
				} else {
					bb.skipBytes(blockLength - 0x10);
				}
			}
		} while (section != 0xf0);
		
		bb.readerIndex(0);
		
		try {
			File fout = new File("working-mta2/Bullet-Dance.bgm");
			Util.writeFile(fout, bb);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		sampleBuffer.release();
		sb.release();
	}
	
}
