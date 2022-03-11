package cam72cam.mod.model.obj;

import cam72cam.mod.math.Vec3d;
import cam72cam.mod.serialization.TagField;

public class OBJGroup {
    @TagField
    public String name;
    @TagField
    public int faceStart;
    @TagField
    public int faceStop;
    @TagField
    public Vec3d min;
    @TagField
    public Vec3d max;

    OBJGroup() {
        // Reflection
        this(null, 0, 0, null, null);
    }

    public OBJGroup(String name, int faceStart, int faceStop, Vec3d min, Vec3d max) {
        this.name = name;
        this.faceStart = faceStart;
        this.faceStop = faceStop;
        this.min = min;
        this.max = max;
    }
}
