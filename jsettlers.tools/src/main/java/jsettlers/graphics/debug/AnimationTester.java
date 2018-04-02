/*******************************************************************************
 * Copyright (c) 2015 - 2017
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
package jsettlers.graphics.debug;

import go.graphics.GLDrawContext;
import go.graphics.area.Area;
import go.graphics.event.GOEvent;
import go.graphics.event.GOKeyEvent;
import go.graphics.region.Region;
import go.graphics.region.RegionContent;
import go.graphics.swing.AreaContainer;
import go.graphics.text.EFontSize;
import go.graphics.text.TextDrawer;
import jsettlers.common.Color;
import jsettlers.common.resources.SettlersFolderChecker;
import jsettlers.common.utils.FileUtils;
import jsettlers.common.utils.OptionableProperties;
import jsettlers.common.utils.mutables.Mutable;
import jsettlers.graphics.image.*;
import jsettlers.graphics.image.Image;
import jsettlers.graphics.reader.AdvancedDatFileReader;
import jsettlers.graphics.reader.AnimationFrameInfo;
import jsettlers.graphics.reader.DatFileType;
import jsettlers.graphics.sequence.Sequence;
import jsettlers.main.swing.resources.ConfigurationPropertiesFile;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Timer;

public class AnimationTester {
	private static final int DAT_FILE_INDEX = 15;

	private static final DatFileType TYPE = DatFileType.RGB565;

	private static final String FILE_NAME_PATTERN = "siedler3_%02d" + TYPE.getFileSuffix();

	private static final Color[] colors = new Color[] { Color.WHITE };

	private static final String ANIMATION_FILE_NAME = String.format(Locale.ENGLISH, FILE_NAME_PATTERN, DAT_FILE_INDEX);
	private final AnimationFrameInfo[][] animationFrameInfos;
	private final Region region;
	private final File settlersGfxFolder;
	private final Map<Integer, AdvancedDatFileReader> fileReaders;
	private final Sequence<Image>[] animationCache;

	private AnimationTester() throws IOException {
		settlersGfxFolder = getSettlersGfxFolder();

		region = new Region(Region.POSITION_CENTER);
		region.setContent(new Content());

		AdvancedDatFileReader animationsReader = new AdvancedDatFileReader(findFileIgnoringCase(settlersGfxFolder, ANIMATION_FILE_NAME), TYPE);
		animationFrameInfos = animationsReader.getAnimations();
		animationCache = new Sequence[animationFrameInfos.length];

		fileReaders = new HashMap<>();
	}

	private AdvancedDatFileReader getFileReader(int fileId){
		if (fileReaders.get(fileId) != null){
			return fileReaders.get(fileId);
		}
		String fileName = String.format(Locale.ENGLISH, FILE_NAME_PATTERN, fileId);
		File file = findFileIgnoringCase(settlersGfxFolder, fileName);
		AdvancedDatFileReader reader = new AdvancedDatFileReader(file, TYPE);
		fileReaders.put(fileId, reader);
		return reader;
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		AnimationTester datFileTester = new AnimationTester();

		Area area = new Area();
		area.add(datFileTester.region);
		Timer redrawTimer = new Timer("opengl-redraw");
		redrawTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				datFileTester.region.requestRedraw();
			}
		}, 100, 25);
		AreaContainer glCanvas = new AreaContainer(area);

		JFrame frame = new JFrame("settlers animation test file " + DAT_FILE_INDEX);
		frame.getContentPane().add(glCanvas);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setSize(new Dimension(400, 400));
		frame.setVisible(true);
	}

	private class Content implements RegionContent {
		private int offsetY = 400;
		private int offsetX = 200;
		private long updateCounter;
		private final int updatesPerFrame = 3;

		public Content() {
			printHelp();
		}

		@Override
		public void handleEvent(GOEvent event) {
			if (event instanceof GOKeyEvent) {
				String keyCode = ((GOKeyEvent) event).getKeyCode();
				if ("UP".equalsIgnoreCase(keyCode)) {
					offsetY -= 400;
				} else if ("DOWN".equalsIgnoreCase(keyCode)) {
					offsetY += 400;
				} else if ("LEFT".equalsIgnoreCase(keyCode)) {
					offsetX += 100;
				} else if ("RIGHT".equalsIgnoreCase(keyCode)) {
					offsetX -= 100;
				}
				region.requestRedraw();
			}
		}

		@Override
		public void drawContent(GLDrawContext gl2, int width, int height) {
			updateCounter++;

			int x = 50;
			int y = 0;
			Color blue = new Color(0xff4286f4);
			gl2.color(blue.getRed(), blue.getGreen(), blue.getBlue(), blue.getAlpha());
			gl2.fillQuad(0,0, width, height);
			gl2.glTranslatef(offsetX, offsetY, 0);
			TextDrawer drawer = gl2.getTextDrawer(EFontSize.NORMAL);

			for (int i = 0; i < animationFrameInfos.length; i++) {
				if (x > -offsetX - 100 && x < -offsetX + width + 100 && y > -offsetY - 100 && y < -offsetY + height + 100) {
					AnimationFrameInfo[] frameInfos = animationFrameInfos[i];
					int currentFrame = (int) (updateCounter / updatesPerFrame % (frameInfos.length - 1));
					AnimationFrameInfo currentInfo = frameInfos[currentFrame];

					AdvancedDatFileReader reader = getFileReader(currentInfo.objectFile);

					Sequence animation = animationCache[i];
					if (animation == null){
						try{
							animation = reader.loadAnimation(frameInfos);
							animationCache[i] = animation;
						} catch (Exception e) {
							System.out.println(e.toString());
							continue;
						}
					}
					SettlerImage image = (SettlerImage) animation.getImage(currentFrame);
					drawImage(gl2, y - image.getHeight() - image.getOffsetY() - currentInfo.posY, 0, x + image.getOffsetX() + currentInfo.posX, image);


//					Sequence sequence = reader.getSettlers().get(currentInfo.objectId);
//
//					if (sequence.length() - 1 < currentInfo.objectFrame){
//						System.out.println( "object : " + currentInfo.objectId + " invalid frame position: " + currentInfo.objectFrame + "(frameInfos: " + frameInfos.length + ") length: " + sequence.length());
//						System.out.println(currentInfo.toString());
//					}
//					else {
//						SettlerImage image = (SettlerImage) sequence.getImage(currentInfo.objectFrame);
//						drawImage(gl2, y - image.getHeight() - image.getOffsetY() - currentInfo.posY, 0, x + image.getOffsetX() + currentInfo.posX, image);
//					}

					gl2.color(0, 0, 0, 1);
					drawer.drawString(-150, y + 40, "Animation " + i + " Object " + currentInfo.objectId);
				}

				y -= 150;

			}
		}

		private void drawImage(GLDrawContext gl2, int y, int index, int x, SettlerImage image) {
			image.drawAt(gl2, x - image.getOffsetX(), y + image.getHeight() + image.getOffsetY(), colors[index % colors.length]);

			gl2.color(1, 0, 0, 1);
			float[] line = new float[] { x, y, 0, x, y + image.getHeight() + image.getOffsetY(), 0, x - image.getOffsetX(),
					y + image.getHeight() + image.getOffsetY(), 0 };
			gl2.drawLine(line, false);
			drawPoint(gl2, x, y);
			drawPoint(gl2, x + image.getWidth(), y);
			drawPoint(gl2, x + image.getWidth(), y + image.getHeight());
			drawPoint(gl2, x, y + image.getHeight());
		}

		private void drawPoint(GLDrawContext gl2, int x, int y) {
		}

		private void printHelp() {
			System.out
					.println("HELP:\nUse arrow keys to navigate.\n");
		}
	}

	private static File getSettlersGfxFolder() throws IOException {
		String settlersFolderPath = new ConfigurationPropertiesFile(new OptionableProperties()).getSettlersFolderValue();
		SettlersFolderChecker.SettlersFolderInfo settlersFolderInfo = SettlersFolderChecker.checkSettlersFolder(settlersFolderPath);
		return settlersFolderInfo.gfxFolder;
	}

	private static File findFileIgnoringCase(File settlersGfxFolder, String fileName) {
		String fileNameLowercase = fileName.toLowerCase();

		Mutable<File> graphicsFile = new Mutable<>();

		FileUtils.iterateChildren(settlersGfxFolder, currentFile -> {
			if (currentFile.isFile() && fileNameLowercase.equalsIgnoreCase(currentFile.getName())) {
				graphicsFile.object = currentFile;
			}
		});

		return graphicsFile.object;
	}
}
