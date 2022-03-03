package cam72cam.mod.render.obj;

import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.model.obj.OBJModel;
import cam72cam.mod.model.obj.VertexBuffer;
import cam72cam.mod.render.OpenGL;
import cam72cam.mod.render.VBO;
import cam72cam.mod.render.opengl.LegacyRenderContext;
import cam72cam.mod.render.opengl.RenderState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;
import util.Matrix4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class OBJVBO extends VBO {
    private final OBJModel model;

    public OBJVBO(OBJModel model) {
        super(model.vbo.get());
        this.model = model;
    }

    public Binding bind(RenderState state) {
        return new Binding(state);
    }

    public class Binding extends VBO.Binding {
        protected Binding(RenderState state) {
            super(state);
        }

        public void draw(Collection<String> groups, Consumer<RenderState> mod) {
            RenderState state = new RenderState();
            mod.accept(state);
            try (OpenGL.With ctx = LegacyRenderContext.INSTANCE.apply(state)) {
                draw(groups);
            }
        }

        /**
         * Draw these groups in the VB
         */
        public void draw(Collection<String> groups) {
            List<String> sorted = new ArrayList<>(groups);
            sorted.sort(Comparator.naturalOrder());
            int start = -1;
            int stop = -1;
            for (String group : sorted) {
                OBJGroup info = model.groups.get(group);
                if (start == stop) {
                    start = info.faceStart;
                    stop = info.faceStop + 1;
                } else if (info.faceStart == stop) {
                    stop = info.faceStop + 1;
                } else {
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, start * 3, (stop - start) * 3);
                    start = info.faceStart;
                    stop = info.faceStop + 1;
                }
            }
            if (start != stop) {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, start * 3, (stop - start) * 3);
            }
        }
    }

    public class Builder {
        private final VertexBuffer vb;
        private float[] built;
        private int builtIdx;

        private Builder() {
            this.vb = model.vbo.get();
            this.built = new float[vb.data.length];
            this.builtIdx = 0;
        }

        private void require(int size) {
            while (built.length <= builtIdx + size) {
                float[] tmp = new float[built.length * 2];
                System.arraycopy(built, 0, tmp, 0, builtIdx);
                built = tmp;
            }
        }
        private void add(float[] buff, Matrix4 m) {
            require(buff.length);

            if (m != null) {
                for (int i = 0; i < buff.length; i += vb.stride) {
                    float x = buff[i+0];
                    float y = buff[i+1];
                    float z = buff[i+2];
                    Vector3f v = m.apply(new Vector3f(x, y, z));
                    buff[i+0] = v.x;
                    buff[i+1] = v.y;
                    buff[i+2] = v.z;
                }
            }

            System.arraycopy(buff, 0, built, builtIdx, buff.length);
            builtIdx += buff.length;
        }

        public void draw() {
            draw((Matrix4) null);
        }

        public void draw(Matrix4 m) {
            if (m == null) {
                add(vb.data, null);
            } else {
                float[] buff = new float[vb.data.length];
                System.arraycopy(vb.data, 0, buff, 0, vb.data.length);
                add(buff, m);
            }
        }

        public void draw(Collection<String> groups) {
            draw(groups, null);
        }

        public void draw(Collection<String> groups, Matrix4 m) {
            for (String group : groups) {
                OBJGroup info = model.groups.get(group);

                int start = info.faceStart * vb.vertsPerFace * vb.stride;
                int stop = (info.faceStop + 1) * vb.vertsPerFace * vb.stride;

                float[] buff = new float[stop - start];
                System.arraycopy(vb.data, start, buff, 0, stop - start);
                add(buff, m);
            }
        }

        public VBO build() {
            float[] out = new float[builtIdx];
            System.arraycopy(built, 0, out, 0, builtIdx);
            return new VBO(new VertexBuffer(out, vb.hasNormals));
        }
    }

    public Builder subModel() {
        return new Builder();
    }
}
