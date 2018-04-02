/*******************************************************************************
 * Copyright (c) 2015
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.graphics.reader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import jsettlers.graphics.image.GuiImage;
import jsettlers.graphics.image.Image;
import jsettlers.graphics.image.LandscapeImage;
import jsettlers.graphics.image.MultiImageMap;
import jsettlers.graphics.image.NullImage;
import jsettlers.graphics.image.SettlerImage;
import jsettlers.graphics.image.ShadowImage;
import jsettlers.graphics.image.SingleImage;
import jsettlers.graphics.image.TorsoImage;
import jsettlers.graphics.reader.bytereader.ByteReader;
import jsettlers.graphics.reader.translator.DatBitmapTranslator;
import jsettlers.graphics.reader.translator.GuiTranslator;
import jsettlers.graphics.reader.translator.LandscapeTranslator;
import jsettlers.graphics.reader.translator.SettlerTranslator;
import jsettlers.graphics.reader.translator.ShadowTranslator;
import jsettlers.graphics.reader.translator.TorsoTranslator;
import jsettlers.graphics.sequence.ArraySequence;
import jsettlers.graphics.sequence.Sequence;

/**
 * This is an advanced dat file reader. It can read the file, but it only reads needed sequences.
 * <p>
 * The format of a dat file is (all numbers in little endian):
 * <table>
 * <tr>
 * <td>Bytes 0..47:</td>
 * <td>Always the same</td>
 * </tr>
 * <tr>
 * <td>Bytes 48 .. 51:</td>
 * <td>file size</td>
 * </tr>
 * <tr>
 * <td>Bytes 52 .. 55:</td>
 * <td>Unknown Pointer, seems not to be a sequence.</td>
 * </tr>
 * <tr>
 * <td>Bytes 56 .. 59:</td>
 * <td>Start position of landscape sequence pointers.</td>
 * </tr>
 * <tr>
 * <td>Bytes 60 .. 63:</td>
 * <td>Unneeded Pointer</td>
 * </tr>
 * <tr>
 * <td>Bytes 64 .. 67:</td>
 * <td>Settler/Building/.. pointers</td>
 * </tr>
 * <tr>
 * <td>Bytes 68 .. 71:</td>
 * <td>Torso pointers</td>
 * </tr>
 * <tr>
 * <td>Bytes 72 .. 75:</td>
 * <td>Position after above</td>
 * </tr>
 * <tr>
 * <td>Bytes 76 .. 79:</td>
 * <td>Position after above</td>
 * </tr>
 * <tr>
 * <td>Bytes 80 .. 83:</td>
 * <td>Something, seems to be like 52..55</td>
 * </tr>
 * <tr>
 * <td>Bytes 84 .. 87:</td>
 * <td>{04 19 00 00}</td>
 * </tr>
 * <tr>
 * <td>Bytes 88 .. 91:</td>
 * <td>{0c 00 00 00}</td>
 * </tr>
 * <tr>
 * <td>Bytes 92 .. 95:</td>
 * <td>{00 00 00 00}</td>
 * </tr>
 * <tr>
 * <td>e.g. Bytes 102 .. 103:</td>
 * <td>Image count of image sequences for one type</td>
 * </tr>
 * <tr>
 * <td>e.g. Bytes 104 .. 107:</td>
 * <td>Start position of fist image sequence list.</td>
 * </tr>
 * </table>
 * 
 * @author michael
 */
public class AdvancedDatFileReader implements DatFileSet {
	/**
	 * Every dat file seems to have to start with this sequence.
	 */
	private static final byte[] FILE_START1 = {
			0x04,
			0x13,
			0x04,
			0x00,
			0x0c,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x54,
			0x00,
			0x00,
			0x00,
			0x20,
			0x00,
			0x00,
			0x00,
			0x40,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x10,
			0x00,
			0x00,
			0x00,
			0x00,
	};
	private static final byte[] FILE_START2 = {
			0x00,
			0x00,
			0x1f,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00
	};

	private static final byte[] FILE_HEADER_END = {
			0x04,
			0x19,
			0x00,
			0x00,
			0x0c,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00
	};

	static final int SEQUENCE_TYPE_COUNT = 8;
	static final int ID_NONE = 0x1904;


	static final int ID_SETTLERS = 0x106;

	static final int ID_TORSOS = 0x3112;

	static final int ID_LANDSCAPE = 0x2412;

	static final int ID_SHADOWS = 0x5982;

	// fullscreen images
	static final int ID_GUIS = 0x11306;

	static final int ID_ANIMATIONINFO = 0x21702;

	static final int ID_PALETTE = 0x2607;

	private final DatBitmapTranslator<SettlerImage> settlerTranslator;

	private final DatBitmapTranslator<TorsoImage> torsoTranslator;

	private final DatBitmapTranslator<LandscapeImage> landscapeTranslator;

	private final DatBitmapTranslator<ShadowImage> shadowTranslator;

	private final DatBitmapTranslator<GuiImage> guiTranslator;

	private ByteReader reader = null;
	private final File file;

	/**
	 * This is a list of file positions where the settler sequences start.
	 */
	private int[] settlerstarts;

	/**
	 * A list of loaded settler sequences.
	 */
	private Sequence<Image>[] settlersequences = null;
	/**
	 * An array with the same length as settlers.
	 */
	private int[] torsostarts;
	/**
	 * An array with the same length as settlers.
	 */
	private int[] shadowstarts;

	/**
	 * A list of loaded landscae images.
	 */
	private LandscapeImage[] landscapeimages = null;
	private final Sequence<LandscapeImage> landscapesequence =
			new LandscapeImageSequence();
	private int[] landscapestarts;

	private GuiImage[] guiimages = null;
	private int[] guistarts;
	private final Sequence<GuiImage> guisequence = new GuiImageSequence();

	private int[] animationinfostarts;

	private final SequenceList<Image> directSettlerList;

	private static final byte[] START = new byte[] {
			0x02, 0x14, 0x00, 0x00, 0x08, 0x00, 0x00
	};

	private final DatFileType type;

	public AdvancedDatFileReader(File file, DatFileType type) {
		this.file = file;
		this.type = type;
		directSettlerList = new DirectSettlerSequenceList();

		settlerTranslator =
				new SettlerTranslator(type);
		torsoTranslator =
				new TorsoTranslator();
		landscapeTranslator =
				new LandscapeTranslator(type);
		shadowTranslator =
				new ShadowTranslator();
		guiTranslator =
				new GuiTranslator(type);
	}

	/**
	 * Initializes the reader, reads the index.
	 */
	@SuppressWarnings("unchecked")
	public void initialize(boolean overrideDifferences) {
		try {
			try {
				System.out.println("loading file " + file.getName());
				reader = new ByteReader(new RandomAccessFile(file, "r"));
				initFromReader(file, reader);

			} catch (IOException e) {
				if (reader != null) {
					reader.close();
					reader = null;
				}
				throw e;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		initializeNullFile();

		landscapeimages = new LandscapeImage[landscapestarts.length];

		guiimages = new GuiImage[guistarts.length];

		settlersequences = new Sequence[settlerstarts.length];

		if (overrideDifferences){
			int torsodifference = settlerstarts.length - torsostarts.length;
			if (torsodifference != 0) {
				int[] oldtorsos = torsostarts;
				torsostarts = new int[settlerstarts.length];
				for (int i = 0; i < oldtorsos.length; i++) {
					torsostarts[i + torsodifference] = oldtorsos[i];
				}
				for (int i = 0; i < torsodifference; i++) {
					torsostarts[i] = -1;
				}
			}

			int shadowdifference = settlerstarts.length - shadowstarts.length;
			if (shadowstarts.length < settlerstarts.length) {
				int[] oldshadows = shadowstarts;
				shadowstarts = new int[settlerstarts.length];
				for (int i = 0; i < oldshadows.length; i++) {
					shadowstarts[i + shadowdifference] = oldshadows[i];
				}
				for (int i = 0; i < shadowdifference; i++) {
					torsostarts[i] = -1;
				}
			}
		}
	}

	private void initFromReader(File file, ByteReader reader)
			throws IOException {
		int[] sequenceIndexStarts =
				readSequenceIndexStarts(file.length(), reader);

		for (int i = 0; i < SEQUENCE_TYPE_COUNT; i++) {
			try {
				readSequencesAt(reader, sequenceIndexStarts[i]);
			} catch (IOException e) {
				System.err.println("Error while loading sequence" + ": "
						+ e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private int[] readSequenceIndexStarts(long filelength,
			ByteReader reader) throws IOException {
		reader.assumeToRead(FILE_START1);
		reader.assumeToRead(type.getFileStartMagic());
		reader.assumeToRead(FILE_START2);
		int fileSize = reader.read32();

		if (fileSize != filelength) {
			throw new IOException(
					"The length stored in the dat file is not the file length.");
		}

		// read settler image pointer
		int[] sequenceIndexStarts = new int[SEQUENCE_TYPE_COUNT];
		for (int i = 0; i < SEQUENCE_TYPE_COUNT; i++) {
			sequenceIndexStarts[i] = reader.read32();
		}

		reader.assumeToRead(FILE_HEADER_END);
		return sequenceIndexStarts;
	}

	/**
	 * reads all sequence starts at a given position.
	 * <p>
	 * Does not align torsos and shadows.
	 * 
	 * @param reader
	 *            The reader to read from.
	 * @param sequenceIndexStart
	 *            The position to start at.
	 * @param type
	 *            The type of the sequence
	 * @throws IOException
	 *             if an read error occurred.
	 */
	private void readSequencesAt(ByteReader reader, int sequenceIndexStart)
			throws IOException {
		// read data index 0
		reader.skipTo(sequenceIndexStart);

		int sequenceType = reader.read32();

		if (sequenceType == ID_NONE || sequenceType == ID_PALETTE){
			return;
		}

		int byteCount = reader.read16();
		int pointerCount = reader.read16();

		if (byteCount != pointerCount * 4 + 8) {
			throw new IOException("Sequence index block length ("
					+ pointerCount + ") and " + "bytecount (" + byteCount
					+ ") are not consistent.");
		}

		int[] sequenceIndexPointers = new int[pointerCount];
		for (int i = 0; i < pointerCount; i++) {
			sequenceIndexPointers[i] = reader.read32();
		}

		if (sequenceType == ID_SETTLERS) {
			settlerstarts = sequenceIndexPointers;
		} else if (sequenceType == ID_TORSOS) {
			torsostarts = sequenceIndexPointers;
		} else if (sequenceType == ID_LANDSCAPE) {
			landscapestarts = sequenceIndexPointers;
		} else if (sequenceType == ID_SHADOWS) {
			shadowstarts = sequenceIndexPointers;
		} else if (sequenceType == ID_GUIS) {
			guistarts = sequenceIndexPointers;
		} else if (sequenceType == ID_ANIMATIONINFO){
			animationinfostarts = sequenceIndexPointers;
		} else {
			System.out.println(String.join(", ", ""+sequenceIndexPointers));
		}
	}

	private void initializeNullFile() {
		if (settlerstarts == null) {
			settlerstarts = new int[0];
		}
		System.out.println("found " + settlerstarts.length + " settler images");
		if (torsostarts == null) {
			torsostarts = new int[0];
		}
		System.out.println("found " + torsostarts.length + " torso images");
		if (shadowstarts == null) {
			shadowstarts = new int[0];
		}
		System.out.println("found " + shadowstarts.length + " shadow images");
		if (landscapestarts == null) {
			landscapestarts = new int[0];
		}
		System.out.println("found " + landscapestarts.length + " landscape images");
		if (guistarts == null) {
			guistarts = new int[0];
		}
		System.out.println("found " + animationinfostarts.length + " animation infos");
		if (animationinfostarts == null){
			animationinfostarts = new int[0];
		}
		System.out.println("found " + guistarts.length + " gui images");
	}

	private void initializeIfNeeded(boolean overrideDifferences) {
		if (settlersequences == null) {
			initialize(overrideDifferences);
		}
	}

	@Override
	public SequenceList<Image> getSettlers() {
		return directSettlerList;
	}

	private static final Sequence<Image> NULL_SETTLER_SEQUENCE =
			new ArraySequence<>(new SettlerImage[0]);

	private class DirectSettlerSequenceList implements SequenceList<Image> {

		@Override
		public Sequence<Image> get(int index) {
			initializeIfNeeded(true);
			if (settlersequences[index] == null) {
				settlersequences[index] = NULL_SETTLER_SEQUENCE;
				try {
					System.out.println("Loading Sequence number " + index);

					loadSettlers(index);
				} catch (Exception e) {
				}
			}
			return settlersequences[index];
		}

		@Override
		public int size() {
			initializeIfNeeded(true);
			return settlersequences.length;
		}
	}

	public AnimationFrameInfo[][] getAnimations() throws IOException {
		initializeIfNeeded(false);
		AnimationFrameInfo[][] animations = new AnimationFrameInfo[animationinfostarts.length][];
		for (int i = 0; i < animationinfostarts.length; i++){
			reader.skipTo(animationinfostarts[i]);
			int frameCount = reader.read32();
			//System.out.println("animationinfo " + i + " frames:" + frameCount);

			AnimationFrameInfo[] animation = new AnimationFrameInfo[frameCount];
			for (int j = 0; j < frameCount; j++){
				int posX = reader.read16signed();
				int posY = reader.read16signed();
				int objectId = reader.read16();
				int objectFile = reader.read16();
				int torsoId = reader.read16();
				int torsoFile = reader.read16();
				int shadowId = reader.read16();
				int shadowFile = reader.read16();
				int objectFrame = reader.read16();
				int torsoFrame = reader.read16();
				int soundFlag1 = reader.read16signed();
				int soundFlag2 = reader.read16signed();

				AnimationFrameInfo animationFrameInfo = new AnimationFrameInfo(
						posX,
						posY,
						objectId,
						objectFile,
						torsoId,
						torsoFile,
						shadowId,
						shadowFile,
						objectFrame,
						torsoFrame,
						soundFlag1,
						soundFlag2);
				// animations are reversed
				animation[frameCount - 1 - j] = animationFrameInfo;
				//System.out.println(info.toString());
			}
			animations[i] = animation;
		}
		return animations;
	}

	public Sequence<Image> loadAnimation(AnimationFrameInfo[] animationFrameInfos) throws IOException {
		initializeIfNeeded(false);
		SettlerImage[] images = new SettlerImage[animationFrameInfos.length];
		for (int i = 0; i < animationFrameInfos.length; i++) {
			AnimationFrameInfo info = animationFrameInfos[i];

			int position = settlerstarts[info.objectId];
			long[] framePositions = readSequenceHeader(position);
			reader.skipTo(framePositions[info.objectFrame]);
			SettlerImage image = DatBitmapReader.getImage(settlerTranslator, reader);

			if (info.torsoId > 0 && info.torsoId != 65535 && torsostarts[info.torsoId] != -1){
				position = torsostarts[info.torsoId];
				framePositions = readSequenceHeader(position);
				if (framePositions.length - 1 < info.torsoFrame){
					System.out.println("torso frame not found");
				}
				else{
					reader.skipTo(framePositions[info.torsoFrame]);
					image.setTorso(DatBitmapReader.getImage(torsoTranslator, reader));
				}
			}

			if (info.shadowId > 0 && shadowstarts[info.shadowId] != 0){
				position = shadowstarts[info.shadowId];
				framePositions = readSequenceHeader(position);

				if (framePositions.length - 1 < info.objectFrame) {
					System.out.println("shadow frame not found");
				}
				else {
					reader.skipTo(framePositions[info.objectFrame]);
					image.setShadow(DatBitmapReader.getImage(shadowTranslator, reader));
				}
			}

			images[i] = image;
		}
		return new ArraySequence<>(images);
	}

	private synchronized void loadSettlers(int index) throws IOException {

		int position = settlerstarts[index];
		long[] framePositions = readSequenceHeader(position);

		SettlerImage[] images = new SettlerImage[framePositions.length];
		for (int i = 0; i < framePositions.length; i++) {
			reader.skipTo(framePositions[i]);
			images[i] = DatBitmapReader.getImage(settlerTranslator, reader);
		}

		int torsoposition = torsostarts[index];
		if (torsoposition >= 0) {
			long[] torsoPositions = readSequenceHeader(torsoposition);
			for (int i = 0; i < torsoPositions.length
					&& i < framePositions.length; i++) {
				reader.skipTo(torsoPositions[i]);
				TorsoImage torso =
						DatBitmapReader.getImage(torsoTranslator, reader);
				images[i].setTorso(torso);
			}
		}

		int shadowposition = shadowstarts[index];
		if (shadowposition >= 0) {
			long[] shadowPositions = readSequenceHeader(shadowposition);
			for (int i = 0; i < shadowPositions.length
					&& i < framePositions.length; i++) {
				reader.skipTo(shadowPositions[i]);
				ShadowImage shadow =
						DatBitmapReader.getImage(shadowTranslator, reader);
				images[i].setShadow(shadow);
			}
		}


		settlersequences[index] = new ArraySequence<>(images);
	}

	private long[] readSequenceHeader(int position) throws IOException {
		reader.skipTo(position);

		reader.assumeToRead(START);
		int frameCount = reader.read8();

		long[] framePositions = new long[frameCount];
		for (int i = 0; i < frameCount; i++) {
			framePositions[i] = reader.read32() + position;
		}
		return framePositions;
	}

	@Override
	public Sequence<LandscapeImage> getLandscapes() {
		return landscapesequence;
	}

	@Override
	public Sequence<GuiImage> getGuis() {
		return guisequence;
	}

	/**
	 * This landscape image list loads the landscape images.
	 * 
	 * @author michael
	 */
	private class LandscapeImageSequence implements Sequence<LandscapeImage> {
		/**
		 * Forces a get of the image.
		 */
		@Override
		public LandscapeImage getImage(int index) {
			initializeIfNeeded(true);
			if (landscapeimages[index] == null) {
				loadLandscapeImage(index);
			}
			return landscapeimages[index];
		}

		@Override
		public int length() {
			initializeIfNeeded(true);
			return landscapeimages.length;
		}

		@Override
		public SingleImage getImageSafe(int index) {
			initializeIfNeeded(true);
			if (index < 0 || index >= length()) {
				return NullImage.getInstance();
			} else {
				if (landscapeimages[index] == null) {
					loadLandscapeImage(index);
				}
				return landscapeimages[index];
			}
		}
	}

	public ByteReader getReaderForLandscape(int index) throws IOException {
		initializeIfNeeded(true);
		reader.skipTo(landscapestarts[index]);
		return reader;
	}

	private void loadLandscapeImage(int index) {
		try {
			reader.skipTo(landscapestarts[index]);
			LandscapeImage image =
					DatBitmapReader.getImage(landscapeTranslator, reader);
			landscapeimages[index] = image;
		} catch (IOException e) {
			landscapeimages[index] = NullImage.getForLandscape();
		}
	}

	/**
	 * This landscape image list loads the landscape images.
	 * 
	 * @author michael
	 */
	private class GuiImageSequence implements Sequence<GuiImage> {
		/**
		 * Forces a get of the image.
		 */
		@Override
		public GuiImage getImage(int index) {
			initializeIfNeeded(true);
			if (guiimages[index] == null) {
				loadGuiImage(index);
			}
			return guiimages[index];
		}

		@Override
		public int length() {
			initializeIfNeeded(true);
			return guiimages.length;
		}

		@Override
		public SingleImage getImageSafe(int index) {
			initializeIfNeeded(true);
			if (index < 0 || index >= length()) {
				return NullImage.getInstance();
			} else {
				if (guiimages[index] == null) {
					loadGuiImage(index);
				}
				return guiimages[index];
			}
		}
	}

	private void loadGuiImage(int index) {
		try {
			reader.skipTo(guistarts[index]);
			GuiImage image = DatBitmapReader.getImage(guiTranslator, reader);
			guiimages[index] = image;
		} catch (IOException e) {
			guiimages[index] = NullImage.getForGui();
		}
	}

	public long[] getSettlerPointers(int seqindex) throws IOException {
		initializeIfNeeded(true);
		return readSequenceHeader(settlerstarts[seqindex]);
	}

	public long[] getTorsoPointers(int seqindex) throws IOException {
		initializeIfNeeded(true);
		int position = torsostarts[seqindex];
		if (position >= 0) {
			return readSequenceHeader(position);
		} else {
			return null;
		}
	}

	/**
	 * Gets a reader positioned at the given settler
	 * 
	 * @param pointer
	 * @return
	 * @throws IOException
	 */
	public ByteReader getReaderForPointer(long pointer) throws IOException {
		initializeIfNeeded(true);
		reader.skipTo(pointer);
		return reader;
	}

	public void generateImageMap(int width, int height, int[] sequences,
			String id) throws IOException {
		initializeIfNeeded(true);

		MultiImageMap map = new MultiImageMap(width, height, id);
		if (!map.hasCache()) {
			map.addSequences(this, sequences, settlersequences);
			map.writeCache();
		}
	}

	public DatBitmapTranslator<SettlerImage> getSettlerTranslator() {
		return settlerTranslator;
	}

	public DatBitmapTranslator<TorsoImage> getTorsoTranslator() {
		return torsoTranslator;
	}

	public DatBitmapTranslator<LandscapeImage> getLandscapeTranslator() {
		return landscapeTranslator;
	}
}