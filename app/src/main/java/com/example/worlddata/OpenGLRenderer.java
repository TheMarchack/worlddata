package com.example.worlddata;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class OpenGLRenderer implements GLSurfaceView.Renderer {

    /** Used for debug logs. */
    private static final String TAG = "Renderer";

    /**
     * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
     * of being located at the center of the universe) to world space. */
    public float[] mModelMatrix = new float[16];

    /**
     * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
     * it positions things relative to our eye. */
    private float[] mViewMatrix = new float[16];
    /** Store the inverse of view matrix. This is used for pointer ray calculations. */
    public float[] mInverseViewMatrix = new float[16];
    /** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
    private float[] mProjectionMatrix = new float[16];
    /** Store the inverse of projection matrix. This is used for pointer ray calculations. */
    public float[] mInverseProjectionMatrix = new float[16];
    /** Allocate storage for the final combined matrix. This will be passed into the shader program. */
    private float[] mMVPMatrix = new float[16];

    /** Store our model data in a float buffer. */
    public final FloatBuffer mObjectPositions;
    public final FloatBuffer mObjectColors;
    public final FloatBuffer mObjectTextures;

    /** This will be used to pass in the transformation matrix. */
    private int mMVPMatrixHandle;
    /** This will be used to pass in the model view matrix. */
    private int mMVMatrixHandle;
    /** This will be used to pass in model position information. */
    private int mPositionHandle;
    /** This will be used to pass in model color information. */
    private int mColorHandle;

    /** These will be used to hold object textures */
    public Canvas DataOverlay;
    public Canvas PointOverlay;
    public Canvas Result;
    public Paint paint;
    public static Bitmap bitmap;
    public static Bitmap dataOverlay;
    public static Bitmap pointOverlay;
    public static Bitmap bitmapSum;

    /** This will be used to pass in the texture. */
    private int mTextureUniformHandle;
    /** This will be used to pass in model texture coordinate information. */
    private int mTextureCoordinateHandle;
    /** This is a handle to our texture data. */
    private static int mTextureDataHandle;

    /** Size of the texture coordinate data in elements. */
    private final int mTextureDataSize = 2;
    /** Size of the position data in elements. */
    private final int mPositionDataSize = 3;
    /** Size of the color data in elements. */
    private final int mColorDataSize = 4;
    /** This is a handle to our per-vertex cube shading program. */
    private int mPerVertexProgramHandle;

    public float xAngle = -70; // X -70 and Y -16 centers initial rotation above Mediterranean
    public float yAngle = -16;
    public float radius = 2f;
    public int sphereStep = 48;
    public float xMovement = 0;
    public float yMovement = 0;
    public int viewportHeight;
    public int viewportWidth;

    public int imageId = R.drawable.map_world_big;
    public int pWidth = 5400;
    public int pHeight = 2700;

    // Position the eye in front of the origin.
    public final float[] eye = {0.0f, 0.0f, 5.0f};
    // We are looking toward the distance
    private final float[] look = {0.0f, 0.0f, -5.0f};
    // Set our up vector. This is where our head would be pointing were we holding the camera.
    private final float[] up = {0.0f, 1.0f, 0.0f};

    // Define the 3D object
    Sphere Object = new Sphere(radius, sphereStep);

    OpenGLView mActivityContext;

    /** Initialize the model data. */
    public OpenGLRenderer( OpenGLView surfaceView) {
        // Initialize the buffers.
        mActivityContext = surfaceView;
        mObjectPositions = Object.objectVertex;
        mObjectColors = Object.objectColor;
        mObjectTextures = Object.objectTexture;
    }

    protected String getVertexShader() {
        final String vertexShader =
                "uniform mat4 u_MVPMatrix;        \n"	// A constant representing the combined model/view/projection matrix.
                        + "uniform mat4 u_MVMatrix;       \n"	// A constant representing the combined model/view matrix.
                        + "attribute vec4 a_Position;     \n"	// Per-vertex position information we will pass in.
                        + "attribute vec4 a_Color;        \n"	// Per-vertex color information we will pass in.
                        + "attribute vec2 a_TexCoordinate;\n"
                        + "varying vec2 v_TexCoordinate;  \n"
                        + "varying vec4 v_Color;          \n"	// This will be passed into the fragment shader.
                        + "void main()                    \n" 	// The entry point for our vertex shader.
                        + "{                              \n"   // Transform the vertex into eye space.
                        + "   vec3 modelViewVertex = vec3(u_MVMatrix * a_Position);\n"    // Multiply the color by the illumination level. It will be interpolated across the triangle.
                        + "   v_Color = a_Color;\n"             // Pass the color through to the fragment shader.
                        +"v_TexCoordinate = a_TexCoordinate;\n"
                        + "   gl_Position = u_MVPMatrix   \n" 	// gl_Position is a special variable used to store the final position.
                        + "               * a_Position;   \n"   // Multiply the vertex by the matrix to get the final point in
                        + "}\n";                                // normalized screen coordinates.
        return vertexShader;
    }

    protected String getFragmentShader() {
        final String fragmentShader =
                "precision mediump float;         \n"		// Set the default precision to medium. We don't need as high of a precision in the fragment shader.
                        + "varying vec4 v_Color;          \n"		// This is the color from the vertex shader interpolated across the triangle per fragment.
                        + "uniform sampler2D u_Texture;   \n"
                        + "varying vec2 v_TexCoordinate;  \n"
                        + "void main()                    \n"		// The entry point for our fragment shader.
                        + "{                              \n"
                        + "   gl_FragColor = (v_Color * texture2D(u_Texture, v_TexCoordinate)); \n"	// Pass the color directly through the pipeline.
                        + "}                              \n";
        return fragmentShader;
    }

    /**
     * Called when the surface is created or recreated.
     * @param gl the GL interface. Use instanceof to test if the interface supports GL11 or higher interfaces.
     * @param config the EGLConfig of the created surface. Can be used to create matching pbuffers.
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Set the background clear color to gray.
        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);

        // Use culling to remove back faces.
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Previous section replaced by this function
        calculateViewMatrix();

        // Invert mViewMatrix for ray calculations
        Matrix.invertM(mInverseViewMatrix, 0, mViewMatrix, 0);

        final String vertexShader = getVertexShader();
        final String fragmentShader = getFragmentShader();

        final int vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mPerVertexProgramHandle = createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[] {"a_Position",  "a_Color", "a_TexCoordinate"});
    }

    /**
     * Updates Angles of the View Matrix based on vertical rotation angle of world model
     * @param yAngle vertical angle of world rotation
     */
    public void updateEyeAngle(float yAngle) {
        eye[1] = (float) Math.sin(yAngle * Math.PI / 180) * 5f;
        eye[2] = (float) Math.cos(yAngle * Math.PI / 180) * 5f;
        look[1] = (float) Math.sin(yAngle * Math.PI / 180) * -5.0f;
        look[2] = (float) Math.cos(yAngle * Math.PI / 180) * -5.0f;
        up[1] = (float) Math.cos(yAngle * Math.PI / 180);
        up[2] = (float) Math.sin(yAngle * Math.PI / 180) * -1.0f;
        calculateViewMatrix();
    }

    /**
     * Set the view matrix. This matrix can be said to represent the camera position.
     * NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
     * view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
     */
    private void calculateViewMatrix() {
        Matrix.setLookAtM(mViewMatrix, 0, eye[0], eye[1], eye[2], look[0], look[1], look[2], up[0], up[1], up[2]);
    }

    /**
     * Called when the surface changed size or after the surface is created and whenever the OpenGL ES surface size changes.
     * @param glUnused the GL interface. Use instanceof to test if the interface supports GL11 or higher interfaces
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);
        // Calculate initial projection matrix
        calculateProjection(width, height, OpenGLView.sizeCoef);
    }

    /**
     * Create a new perspective projection matrix.
     * The height will stay the same while the width will vary as per aspect ratio.
     * Added scale factor for zoom functionality.
     * @param width
     * @param height
     * @param scale
     */
    public void calculateProjection(int width, int height, float scale) {
        final float ratio = (float) width / height;
        final float left = -ratio * scale;
        final float right = ratio * scale;
        final float bottom = -1.0f * scale;
        final float top = 1.0f * scale;
        final float near = 1.0f;
        final float far = 10.0f;

        // Calculate projection matrix
        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
        // Invert projection matrix for ray calculations
        Matrix.invertM(mInverseProjectionMatrix, 0, mProjectionMatrix, 0);
    }

    /**
     * Called to draw the current frame
     * @param gl the GL interface. Use instanceof to test if the interface supports GL11 or higher interfaces
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mPerVertexProgramHandle);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVMatrix");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_Texture");
        mPositionHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Color");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_TexCoordinate");

        // Draw the object
        float slowCoefficient = 0.93f;
        float verticalMovementRatio = 0.7f;
        int maxAngle = 75;

        Matrix.setIdentityM(mModelMatrix, 0);
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);
        // Slowing down yMovement and xMovement
        if (Math.abs(xMovement) < 0.08) {
            xMovement = 0;
        } else {
            xMovement = Math.round(xMovement * slowCoefficient * 100) / 100f;
        }
        if (Math.abs(yMovement) < 0.08) {
            yMovement = 0;
        } else {
            yMovement = Math.round(yMovement * slowCoefficient * 100) / 100f;
        }
        xAngle += xMovement;
        yAngle += yMovement * verticalMovementRatio;

        // Restrict yAngle to +/- maxAngle degrees
        yAngle = Math.min(maxAngle, Math.max(-maxAngle, yAngle));
        // Restrict xAngle to 0 - 360 degrees
        xAngle = (xAngle + 360) % 360;

        // Rotate Eye angle by yAngle
        updateEyeAngle(-yAngle);

        Matrix.rotateM(mModelMatrix, 0, 0, 1.0f, 0.0f, 0.0f); // pitch  // -yAngle
        Matrix.rotateM(mModelMatrix, 0, -xAngle, 0.0f, 1.0f, 0.0f); // roll

        // Set the active texture unit
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        // Update existing texture|
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0, bitmapSum);
        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureDataHandle);
        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        drawObject();
    }

    /**
     * Draws the object. */
    private void drawObject() {
        // Pass in the position information
        mObjectPositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,
                0, mObjectPositions);
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the color information
        mObjectColors.position(0);
        GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,
                0, mObjectColors);
        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Pass in the texture coordinate information.
        mObjectTextures.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureDataSize, GLES20.GL_FLOAT, false,
                0, mObjectTextures);
        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        // Draw the object.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, Object.mTriangles * 3);
    }



    /**
     * Helper function to compile a shader.
     * @param shaderType The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader. */
    private int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(shaderHandle, shaderSource);

            // Compile the shader.
            GLES20.glCompileShader(shaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0)
            {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }
        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }
        return shaderHandle;
    }

    /**
     * Helper function to compile and link a program.
     * @param vertexShaderHandle An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @param attributes Attributes that need to be bound to the program.
     * @return An OpenGL handle to the program. */
    private int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES20.glCreateProgram();

        if (programHandle != 0) {
            // Bind the vertex shader to the program.
            GLES20.glAttachShader(programHandle, vertexShaderHandle);

            // Bind the fragment shader to the program.
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            if (attributes != null) {
                final int size = attributes.length;
                for (int i = 0; i < size; i++)
                {
                    GLES20.glBindAttribLocation(programHandle, i, attributes[i]);
                }
            }

            // Link the two shaders together into a program.
            GLES20.glLinkProgram(programHandle);
            mTextureDataHandle = loadTexture(mActivityContext, imageId);

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES20.glGetProgramInfoLog(programHandle));
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }
        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }
        return programHandle;
    }

    /**
     * Refreshes world model textures by layering data and point layer over main texture
     */
    private void refreshBitmaps() {
        // Put overlay Bitmap on map Bitmap
        Rect rectangle = new Rect(0, 0, pWidth, pHeight);
        Result.drawBitmap(bitmap, new Rect(0, 0, pWidth, pHeight), rectangle, null);
        Result.drawBitmap(dataOverlay, 0, 0, null);
        Result.drawBitmap(pointOverlay, 0, 0, null);
    }


    /**
     * Loads world model texture and creates layers for data.
     * @param mActivityContext2
     * @param resourceId Resource Id of the image to be used as world model texture.
     * @return
     */
    public int loadTexture(GLSurfaceView mActivityContext2, final int resourceId)
    {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0)
        {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling

            // Read in the resources and make them mutable (except original map)
            bitmap = BitmapFactory.decodeResource(mActivityContext2.getResources(), resourceId, options);
            dataOverlay = Bitmap.createBitmap(pWidth, pHeight, Bitmap.Config.ARGB_8888);
            dataOverlay = dataOverlay.copy(Bitmap.Config.ARGB_8888, true);
            pointOverlay = Bitmap.createBitmap(pWidth, pHeight, Bitmap.Config.ARGB_8888);
            pointOverlay = pointOverlay.copy(Bitmap.Config.ARGB_8888, true);
            bitmapSum = Bitmap.createBitmap(pWidth, pHeight, Bitmap.Config.ARGB_8888);
            bitmapSum = bitmapSum.copy(Bitmap.Config.ARGB_8888, true);

            GLES20.glGenTextures(1, textureHandle, 0);

            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

            // Load the bitmapSum into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapSum, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

            // Prepare texture layers
            DataOverlay = new Canvas(dataOverlay);
            PointOverlay = new Canvas(pointOverlay);
            Result = new Canvas(bitmapSum);
            paint = new Paint();

            refreshBitmaps();
        }
        if (textureHandle[0] == 0)
        {
            throw new RuntimeException("Error loading texture.");
        }
        return textureHandle[0];
    }

    /**
     * Draws an ellipse on given canvas.
     * @param layer Canvas object created from Bitmap used to create world model texture.
     * @param x Horizontal pixel coordinate on Canvas from left side.
     * @param y Vertical pixel coordinate on Canvas from the top.
     * @param r Radius of the ellipse.
     * @param color Color of the ellipse.
     */
    public void drawEllipse(Canvas layer, float x, float y, float r, Paint color) {
        // Calculate elongation to compensate spherical projection of texture
        float c = (float) (Math.sin(y / pHeight * Math.PI));
        float dotWidth = r / c;
        // Draw an ellipse and correct if too close to the edge of texture
        layer.drawOval(x - dotWidth, y - r, x + dotWidth, y + r, color);
        if (x < dotWidth) {
            layer.drawOval(pWidth + x - dotWidth, y - r, pWidth + x + dotWidth, y + r, color);
        } else if (pWidth - x < dotWidth) {
            layer.drawOval(x - pWidth - dotWidth, y - r, x - pWidth + dotWidth, y + r, color);
        }
    }

    /**
     * Draws a square point on given canvas.
     * @param layer Canvas object created from Bitmap used to create world model texture.
     * @param x Horizontal pixel coordinate on Canvas from left side.
     * @param y Vertical pixel coordinate on Canvas from the top.
     * @param r Radial size of the square.
     */
    public void drawSquarePoint(int[][] layer, float x, float y, float r) {
        // Calculate elongation to compensate spherical projection of texture
        float c = (float) (Math.sin(y / pHeight * Math.PI));
        float dotWidth = r / c;
        // Draw the pixels in a square shape
        for (int i=Math.round(x - dotWidth); i<Math.round(x + dotWidth); i++) {
            for (int j=Math.round(y-r); j<Math.round(y+r); j++) {
                layer[i][j]++;
            }
        }
    }

    /**
     * Draws a circle around the selected point on a Canvas.
     * @param x Horizontal pixel coordinate on Canvas from left side.
     * @param y Vertical pixel coordinate on Canvas from the top.
     */
    public void drawCircle(float x, float y) {
        // Prepare overlay and paint style
        pointOverlay.eraseColor(Color.TRANSPARENT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);

        // Draw the point
        drawEllipse(PointOverlay, x, y, 7, paint);
        refreshBitmaps();
    }

    /**
     * Returns max value of a 2D integer array.
     * @param array Array of integers to be evaluated.
     * @return Max value of the array
     */
    public int getMaxVal(int[][] array) {
        int maxVal = 0;
        for (int i=0; i<pWidth; i++) {
            for (int j=0; j<pHeight; j++) {
                if (array[i][j] > maxVal) maxVal = array[i][j];
            }
        }
        return maxVal;
    }

    /**
     * Draws categorized points of binary data from GeoJSON features on specific color channel Bitmaps.
     * @param features List of feature (point) data from loaded GeoJSON file.
     */
    public void drawCategories(JsonArray features) {
        // Prepare array for overlay
        int[][] overlayArrayBlue = new int[pWidth][pHeight];
        int[][] overlayArrayGreen = new int[pWidth][pHeight];
        int[][] overlayArrayRed = new int[pWidth][pHeight];

        // Prepare array for calculating view position
        List<Float> xCoords = new ArrayList<>();
        List<Float> yCoords = new ArrayList<>();

        // Loop to draw the points with point counters
        int counterRed = 0;
        int counterBlue = 0;
        for (int i = 0; i < features.size(); i++) {
            // Get specific point data from GeoJSON object
            JsonObject point = features.get(i).getAsJsonObject();
            int category = point.get("properties").getAsJsonObject()
                    .get("category").getAsInt();
            JsonArray coords = point.get("geometry").getAsJsonObject()
                    .getAsJsonArray("coordinates");
            xCoords.add(coords.get(0).getAsFloat());
            yCoords.add(coords.get(1).getAsFloat());
            int x = Math.round((coords.get(0).getAsFloat() + 180) / 360 * pWidth);
            int y = Math.round(-(coords.get(1).getAsFloat() - 90) / 180 * pHeight);

            if (category == 1) {
                counterBlue++;
                drawSquarePoint(overlayArrayBlue, x, y, 1);
                //overlayArrayBlue[x][y]++;
            } else if (category == 2) {
                counterRed++;
                drawSquarePoint(overlayArrayRed, x, y, 1);
                //overlayArrayRed[x][y]++;
            }
        }

        // Find max pixel value
        int maxValBlue = getMaxVal(overlayArrayBlue);
        int maxValRed = getMaxVal(overlayArrayRed);

        // Get overlay range
        float[] minMax = goToData(xCoords, yCoords);

        // Merge color arrays to get overlay and reload texture
        mergeLayers(minMax, overlayArrayRed, overlayArrayGreen, overlayArrayBlue, Math.max(maxValBlue, maxValRed));
        refreshBitmaps();

        Log.i("Drawing", counterBlue + " blue points and " + counterRed + " red points drawn.");
    }

    /**
     * Merges different color layers of binary category data from drawCategories function to get dataOverlay Bitmap.
     * @param minMax List of float variables showing bordering coordinates of point data - minX, maxX, minY and maxY.
     * @param red 2D array of point occurences in every pixel of red color.
     * @param green 2D array of point occurences in every pixel of green color.
     * @param blue 2D array of point occurences in every pixel of blue color.
     * @param maxVal Maximum value of all three color channels. The most points drawn in one pixel.
     */
    public void mergeLayers(float[] minMax, int[][] red, int[][] green, int[][] blue, int maxVal) {
        dataOverlay.eraseColor(Color.TRANSPARENT);
        int r, g, b, alpha, max;
        float coef;
        int minx = (int) Math.round((minMax[0] + 180) / 360 * pWidth);
        int maxx = (int) Math.round((minMax[1] + 180) / 360 * pWidth);
        int miny = (int) Math.round(-(minMax[3] - 90) / 180 * pHeight);
        int maxy = (int) Math.round(-(minMax[2] - 90) / 180 * pHeight);

        for (int i=minx; i<maxx; i++) {
            for (int j=miny; j<maxy; j++) {
                r = red[i][j];
                g = green[i][j];
                b = blue[i][j];
                max = Math.max(r, Math.max(g, b));
                if (max > 0) {
                    coef = (float) 255 / Math.max(max, 1);
                    r = Math.round(r * coef);
                    g = Math.round(g * coef);
                    b = Math.round(b * coef);
                }
                if (max == 0) {
                    alpha = 0;
                } else {
                    alpha = Math.round(100 + ((float) max / maxVal) * 155);
                }
                dataOverlay.setPixel(i, j, Color.argb(alpha, r, g, b));
            }
        }
    }

    /**
     * Draws single colored multi category data on to the world model texture.
     * @param features List of feature (point) data from loaded GeoJSON file.
     */
    public void drawPoints(JsonArray features) {
        // Prepare overlay and paint style
        dataOverlay.eraseColor(Color.TRANSPARENT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);

        // Prepare array for calculating view position
        List<Float> xCoords = new ArrayList<>();
        List<Float> yCoords = new ArrayList<>();

        // Draw the points
        int counter = 0;
        for (int i = 0; i < features.size(); i++) {
            JsonObject point = features.get(i).getAsJsonObject();
            JsonArray coords = point.get("geometry").getAsJsonObject()
                    .getAsJsonArray("coordinates");
            xCoords.add(coords.get(0).getAsFloat());
            yCoords.add(coords.get(1).getAsFloat());
            float x = (coords.get(0).getAsFloat() + 180) / 360 * pWidth;
            float y = -(coords.get(1).getAsFloat() - 90) / 180 * pHeight;

            drawEllipse(DataOverlay, x, y, 1, paint);
            counter++;
        }
        Log.i("Drawing", counter + " points drawn.");
        refreshBitmaps();
        goToData(xCoords, yCoords);
    }

    /**
     * Sets world model rotation to the middle of displayed data and zoom level to encapsulate all points.
     * @param xList List of X coordinates of all points.
     * @param yList List of Y coordinates of all points.
     * @return returns a list of float variables showing bordering coordinates of point data - minX, maxX, minY and maxY - used in function mergeLayers.
     */
    private float[] goToData(List<Float> xList, List<Float> yList) {
        float minX = 180;
        float minY = 90;
        float maxX = -180;
        float maxY = -90;
        // Get min and max point coordinates
        for (int i = 0; i<xList.size(); i++) {
            float x = xList.get(i);
            float y = yList.get(i);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        // Check if zoom should happen over edge of map
        float midX;
        if (maxX - minX < (180 - maxX) + minX) {
            midX = (maxX + minX) / 2;
        } else {
            midX = (maxX + minX + 360) / 2;
        }
        float midY = (maxY + minY) / 2;

        // Set view to middle of data area and adjust zoom
        xAngle = midX - 90f;
        yAngle = -midY;
        OpenGLView.sizeCoef = Math.max(OpenGLView.maxZoom, Math.min(1f, (maxX - minX + 10) / 120 ));

        // Return min and max values
        return new float[]{minX, maxX, minY, maxY};
    }

    /** Swaps the texture of the world model from geographic to land mass shape. */
    public void swapTexture() {
        mActivityContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (imageId == R.drawable.map_world_big) {
                    imageId = R.drawable.map_world_land_ocean;
                    pWidth = 4832;
                    pHeight = 2416;
                } else {
                    imageId = R.drawable.map_world_big;
                    pWidth = 5400;
                    pHeight = 2700;
                }
                mTextureDataHandle = loadTexture(mActivityContext, imageId);
            }
        });
    }
}
