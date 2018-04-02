package jsettlers.graphics.reader;

public class AnimationFrameInfo {
    public final int posX;
    public final int posY;
    public final int objectId;
    public final int objectFile;
    public final int torsoId;
    public final int torsoFile;
    public final int shadowId;
    public final int shadowFile;
    public final int objectFrame;
    public final int torsoFrame;
    public final int soundFlag1;
    public final int soundFlag2;

    public AnimationFrameInfo(
            int posX,
            int posY,
            int objectId,
            int objectFile,
            int torsoId,
            int torsoFile,
            int shadowId,
            int shadowFile,
            int objectFrame,
            int torsoFrame,
            int soundFlag1,
            int soundFlag2) {
        this.posX = posX;
        this.posY = posY;
        this.objectId = objectId;
        this.objectFile = objectFile;
        this.torsoId = torsoId;
        this.torsoFile = torsoFile;
        this.shadowId = shadowId;
        this.shadowFile = shadowFile;
        this.objectFrame = objectFrame;
        this.torsoFrame = torsoFrame;
        this.soundFlag1 = soundFlag1;
        this.soundFlag2 = soundFlag2;
    }
    
    public String toString(){
        return "posX " + posX + ", posY " + posY + ", objectId " + objectId + ", objectFile " + objectFile + ", torsoId " + torsoId + ", torsoFile " + torsoFile + ", shadowId " + shadowId + ", shadowFile " + shadowFile + ", objectFrame " + objectFrame + ", torsoFrame " + torsoFrame + ", soundFlag1 " + soundFlag1 + ", soundFlag2 " + soundFlag2;
    }
}
