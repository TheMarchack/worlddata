package com.example.worlddata;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.gson.JsonArray;

public class OpenGLView extends GLSurfaceView {
    // Variables for touch interaction
    private float touchX = 0;
    private float touchY = 0;
    private float lastTouchDistance;
    public static float maxZoom = 0.15f;
    public static float sizeCoef = 1;
    private boolean ignoreOnce = false; // Ignore movement measurement once after releasing second finger
    private boolean movementDetected = false; // Don't calculate touch coordinates if movement detected before

    OpenGLRenderer renderer;

    public OpenGLView(Context context) {
        super(context);
        init();
    }

    public OpenGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer = new OpenGLRenderer( this));
    }

    /**
     * Handles touch events. Distinguishes these motions:
     * - single finger tap for drawing a circle on world model texture;
     * - single finger motion for rotation;
     * - two finger motion for zoom.
     * @param event Touch motion detected by system.
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int points = event.getPointerCount();
        final int action = event.getAction();
        float touchDistance;
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: { // One finger down
                touchX = event.getX();
                touchY = event.getY();
            }
            break;
            case MotionEvent.ACTION_POINTER_DOWN: { // Other finger down
                movementDetected = true;
                touchDistance = getTouchedDistance(event);
                lastTouchDistance = touchDistance;
            }
            break;
            case MotionEvent.ACTION_MOVE: { // Finger(s) move
                movementDetected = true;
                if (points == 1) {
                    // Calculate movement
                    if (ignoreOnce) {
                        ignoreOnce = false;
                    } else {
                        renderer.xMovement = (touchX - event.getX()) / 5f * sizeCoef;
                        renderer.yMovement = (touchY - event.getY()) / 5f * sizeCoef;
                    }
                    // Get new reading
                    touchX = event.getX(0);
                    touchY = event.getY(0);
                } else if (points == 2) {
                    touchDistance = getTouchedDistance(event);
                    sizeCoef = Math.max(maxZoom, Math.min(1f, sizeCoef * lastTouchDistance/touchDistance));
                    renderer.calculateProjection(renderer.viewportWidth, renderer.viewportHeight, sizeCoef);
                    lastTouchDistance = touchDistance;
                    // update firstTouch coordinates
                    touchX = event.getX(0);
                    touchY = event.getY(0);
                }
            }
            break;
            case MotionEvent.ACTION_POINTER_UP: { // Other finger up
                ignoreOnce = true;
            }
            break;
            case MotionEvent.ACTION_UP: {
                if (!movementDetected) { // Last finger up
                    try {
                        float angleX = renderer.xAngle - 90f; // compensation for texture shift
                        float angleY = -renderer.yAngle;

                        float angleXrad = (float) (angleX * Math.PI / 180);
                        float angleYrad = (float) (angleY * Math.PI / 180);

                        // Get touched point polar coordinates
                        float[] intersect = intersectionPoint(castRay(touchX, touchY), renderer.radius);
                        int touched = Float.compare(intersect[0], Float.NaN);
                        float[] intersectRotated = rotatePoint(intersect, angleYrad);
                        float[] polar = getPolar(intersectRotated[0], intersectRotated[1], intersectRotated[2]);

                        polar[0] += angleXrad;
                        // Restrict X to 0 - 2*PI radians
                        polar[0] = (float) ((polar[0] + Math.PI * 2) % (Math.PI * 2));
                        // Restrict Y to 0 - PI radians
                        polar[1] = (float) Math.min(Math.PI / 2, Math.max(-Math.PI / 2, polar[1]));

                        // Only draw point if not on poles
                        if (polar[1] > -Math.PI / 2 + 0.1 && polar[1] < Math.PI / 2 - 0.1) {
                            float[] mapLoc = new float[2];
                            mapLoc[0] = (float) (polar[0] / (Math.PI * 2));
                            mapLoc[1] = (float) ((polar[1] + Math.PI / 2) / Math.PI);

                            renderer.drawCircle(mapLoc[0] * renderer.pWidth, mapLoc[1] * renderer.pHeight);
                        }
                        // Show coordinates if clicked on sphere
                        if (touched != 0) {
                            showCoordinates(polar);
                        } else {
                            MainActivity.getInstance().setText("");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                movementDetected = false;
            }
            break;
            default:
                throw new IllegalStateException("Unexpected value: " + action); // (action & MotionEvent.ACTION_MASK));
        }
        return true;
    }

    /**
     * Calculates coordinates of touched point on world model and displays on the bottom of screen.
     * @param polar Polar coordinates of the point.
     */
    private void showCoordinates(float[] polar) {
        float[] coordinates = new float[2];
        coordinates[0] = Math.round((polar[0] - Math.PI) / Math.PI * 180 * 100f) / 100f;
        coordinates[1] = Math.round(polar[1] / (Math.PI / 2) * 90 * 100f) / 100f;
        String longitude = (coordinates[0] < 0) ? "°W " : "°E ";
        String latitude = (coordinates[1] < 0) ? "°N " : "°S ";

        MainActivity.getInstance().setText(Math.abs(coordinates[0]) + longitude + "; "
                + Math.abs(coordinates[1]) + latitude);
    }

    /**
     * Calculates coordinates of intersection between world model surface and touch ray line as a 4 by 4 matrix.
     * @param vector 4 by 4 matrix of ray vector in the direction of touched point on screen.
     * @param radius Radius of the model of a sphere.
     * @return 4 by 4 matrix with the position of touched point on the sphere.
     */
    private float[] intersectionPoint(float[] vector, float radius) {
        float[] origin = {0f, 0f, 5f};
        float[] intersection = new float[4];
        float a, b, c, t;

        a = vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2];
        b = origin[0] * vector[0] * 2 + origin[1] * vector[1] * 2 + origin[2] * vector[2] * 2;
        c = origin[0]*origin[0] + origin[1]*origin[1] + origin[2]*origin[2] - radius*radius;

        t = minRoot(a, b, c);
        intersection[0] = origin[0] + vector[0] * t;
        intersection[1] = origin[1] + vector[1] * t;
        intersection[2] = origin[2] + vector[2] * t;
        intersection[3] = 1.0f;
        return intersection;
    }

    /**
     * Calculates minimum root of quadratic function with coefficients a, b and c.
     * @param a
     * @param b
     * @param c
     * @return Smallest root of functions as a Float.
     */
    private float minRoot(float a, float b, float c) {
        float root1, root2, sqrtD;
        sqrtD = (float) Math.sqrt((b*b) - (4*a*c));
        root1 = (-b + sqrtD) / (2*a);
        root2 = (-b - sqrtD) / (2*a);
        return Math.min(root1, root2);
    }

    /**
     * Calculates polar coordinates from 3D X, Y and Z point coordinates.
     * @param x
     * @param y
     * @param z
     * @return Polar coordinates as two angles in radians. Distance from origin is defined in OpenGLRenderer.radius.
     */
    private float[] getPolar(float x, float y, float z) {
        float[] polarCoords = new float[2];
        float h = (float) Math.sqrt(x*x + z*z);
        float r = renderer.radius;
        polarCoords[0] = (float) Math.asin(x/h);
        polarCoords[1] = (float) Math.asin(y/r);
        return polarCoords;
    }

    /**
     * Calculates line matrix of ray originating from camera to the point of touch.
     * @param posX Horizontal position of touched point on screen.
     * @param posY Vertical position of touched point on screen.
     * @return 4 by 4 line matrix.
     * @throws InterruptedException
     */
    private float[] castRay(float posX, float posY) throws InterruptedException {
        float[] pointPosition = new float[4];
        pointPosition[0] = (2.0f * posX) / renderer.viewportWidth - 1.0f;
        pointPosition[1] = (2.0f * posY) / renderer.viewportHeight - 1.0f;
        pointPosition[2] = -1.0f;
        pointPosition[3] = 1.0f;

        // get touch ray line matrix
        float[] touchRay = multiplyMat4ByVec4(renderer.mInverseProjectionMatrix, pointPosition);
        touchRay[2] = -1.0f;
        touchRay[3] = 0.0f;

        return multiplyMat4ByVec4(renderer.mInverseViewMatrix, touchRay);
    }

    /**
     * Performs rotation of point location for selected point coordinate calculations.
     * @param pointV4
     * @param yAngle
     * @return
     */
    private float[] rotatePoint(float[] pointV4, float yAngle) {
        float cosY = (float) Math.cos(yAngle);
        float sinY = (float) Math.sin(yAngle);

        float[] rotationMatrixY = { 1, 0, 0, 0,
                                    0, cosY, -sinY, 0,
                                    0, sinY, cosY, 0,
                                    0, 0, 0, 1 };
        return multiplyMat4ByVec4(rotationMatrixY, pointV4);
    }

    /**
     * Performs multiplication of 4 by 4 matrix and a 4D vector
     * @param matrix4
     * @param vector4
     * @return
     */
    private float[] multiplyMat4ByVec4(float[] matrix4, float[] vector4) {
        float[] returnMatrix = new float[4];
        returnMatrix[0] = (matrix4[0] * vector4[0]) + (matrix4[1] * vector4[1]) + (matrix4[2] * vector4[2]) + (matrix4[3] * vector4[3]);
        returnMatrix[1] = (matrix4[4] * vector4[0]) + (matrix4[5] * vector4[1]) + (matrix4[6] * vector4[2]) + (matrix4[7] * vector4[3]);
        returnMatrix[2] = (matrix4[8] * vector4[0]) + (matrix4[9] * vector4[1]) + (matrix4[10] * vector4[2]) + (matrix4[11] * vector4[3]);
        returnMatrix[3] = (matrix4[12] * vector4[0]) + (matrix4[13] * vector4[1]) + (matrix4[14] * vector4[2]) + (matrix4[15] * vector4[3]);
        return returnMatrix;
    }

    /**
     * Calculates distance between first two points of contact on screen.
     * @param event Touch motion detected by system.
     * @return
     */
    private float getTouchedDistance(MotionEvent event) {
        float x1 = event.getX(0);
        float y1 = event.getY(0);
        float x2 = event.getX(1);
        float y2 = event.getY(1);
        return (float) Math.sqrt(Math.pow(Math.abs(x2 - x1), 2) + Math.pow(Math.abs(y2 - y1), 2));
    }

    /** Calls the swapTexture function from OpenGLRenderer. */
    public void swapTexture() {
        renderer.swapTexture();
    }

    /**
     * Calls the drawPoints function from OpenGLRenderer.
     * @param features List of feature (point) data from loaded GeoJSON file.
     */
    public void drawPoints(JsonArray features) { renderer.drawPoints(features); }

    /**
     * Calls the drawCategories function from OpenGLRenderer.
     * @param features List of feature (point) data from loaded GeoJSON file.
     */
    public void drawCategories(JsonArray features) { renderer.drawCategories(features); }
}
