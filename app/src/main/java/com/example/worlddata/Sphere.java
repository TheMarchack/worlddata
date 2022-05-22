package com.example.worlddata;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

public class Sphere {

    /** How many bytes per float. */
    private final int mBytesPerFloat = 4;
    private final float[] white = {1.0f, 1.0f, 1.0f, 1.0f};

    public final FloatBuffer objectVertex;
    public final FloatBuffer objectColor;
    public final FloatBuffer objectTexture;

    private final double mRaduis;
    private final int mStep;
    public int mTriangles;

    /**
     * Creates the Sphere.
     * @param radius Radius defines the distance of surface from origin.
     * @param step Value of step defines the size of each facet as well as the number of facets.
     * */
    public Sphere( float radius, int step) {
        this.mRaduis = radius;
        this.mStep = step;

        mTriangles = 4 * mStep * (mStep - 1);
        int mPoints = 3 * mTriangles;

        objectVertex = ByteBuffer.allocateDirect(mPoints * 3 * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        objectColor = ByteBuffer.allocateDirect(mPoints * 4 * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        objectTexture = ByteBuffer.allocateDirect(mPoints * 2 * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        build();
    }

    private void build() {
        /**
         * Builds the spherical model.
         * z and y switched because z axis is horizontal and y is vertical (rotation axis for sphere)
         * x = p * sin(phi) * cos(theta)
         * z = p * cos(phi)
         * y = p * sin(phi) * sin(theta)
         */
        double dTheta = (double) Math.PI / mStep;

        // Generate triangle coordinates for the sphere
        // for each horizontal line
        for (int i=0; i < mStep; i++) {
            double phiU = i * dTheta;
            double phiL = (i+1) * dTheta;
            // for each meridian
            for (int j=0; j < mStep * 2; j++) {
                double thetaL = j * dTheta;
                double thetaR = ((j+1) * dTheta) % (2 * mStep * dTheta);
                if (i == 0) {
                    // Create triangles at top pole
                    objectVertex.put(0f); objectVertex.put(calcZ(phiU)); objectVertex.put(0f);
                    objectVertex.put(calcX(phiL, thetaR)); objectVertex.put(calcZ(phiL)); objectVertex.put(calcY(phiL, thetaR));
                    objectVertex.put(calcX(phiL, thetaL)); objectVertex.put(calcZ(phiL)); objectVertex.put(calcY(phiL, thetaL));
                    paintTriangle(white);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put(0f);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put((i+1)*1f/mStep);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put((i+1)*1f/mStep);
                } else if (i == mStep-1) {
                    // Create triangles at bottom pole
                    objectVertex.put(calcX(phiU, thetaL)); objectVertex.put(calcZ(phiU)); objectVertex.put(calcY(phiU, thetaL));
                    objectVertex.put(calcX(phiU, thetaR)); objectVertex.put(calcZ(phiU)); objectVertex.put(calcY(phiU, thetaR));
                    objectVertex.put(0f); objectVertex.put(calcZ(phiL)); objectVertex.put(0f);
                    paintTriangle(white);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put(i*1f/mStep);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put(i*1f/mStep);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put(1f);
                } else {
                    // Create two triangles for each trapezoid
                    objectVertex.put(calcX(phiU, thetaL)); objectVertex.put(calcZ(phiU)); objectVertex.put(calcY(phiU, thetaL));
                    objectVertex.put(calcX(phiL, thetaR)); objectVertex.put(calcZ(phiL)); objectVertex.put(calcY(phiL, thetaR));
                    objectVertex.put(calcX(phiL, thetaL)); objectVertex.put(calcZ(phiL)); objectVertex.put(calcY(phiL, thetaL));
                    paintTriangle(white);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put(i*1f/mStep);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put((i+1)*1f/mStep);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put((i+1)*1f/mStep);

                    objectVertex.put(calcX(phiU, thetaL)); objectVertex.put(calcZ(phiU)); objectVertex.put(calcY(phiU, thetaL));
                    objectVertex.put(calcX(phiU, thetaR)); objectVertex.put(calcZ(phiU)); objectVertex.put(calcY(phiU, thetaR));
                    objectVertex.put(calcX(phiL, thetaR)); objectVertex.put(calcZ(phiL)); objectVertex.put(calcY(phiL, thetaR));
                    paintTriangle(white);
                    objectTexture.put(1-j*0.5f/mStep); objectTexture.put(i*1f/mStep);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put(i*1f/mStep);
                    objectTexture.put(1-(j+1)*0.5f/mStep); objectTexture.put((i+1)*1f/mStep);
                }
            }
        }
        objectVertex.position(0);
        objectColor.position(0);
        objectTexture.position(0);
    }

    /**
     * Calculates X coordinate from polar coordinate angles phi and theta.
     * @param phi
     * @param theta
     * @return Value of X as Float.
     */
    private float calcX(double phi, double theta) {
        return(float) (mRaduis * Math.sin(phi) * Math.cos(theta));
    }

    /**
     * Calculates Y coordinate from polar coordinate angles phi and theta.
     * @param phi
     * @param theta
     * @return Value of Y as Float.
     */
    private float calcY(double phi, double theta) {
        return (float) (mRaduis * Math.sin(phi) * Math.sin(theta));
    }

    /**
     * Calculates Z coordinate using only phi angle from polar coordinates.
     * @param phi
     * @return Value of Z as Float.
     */
    private float calcZ(double phi) {
        return (float) (mRaduis * Math.cos(phi));
    }

    /**
     * Paints triangle in a random color. Used for debugging.
     */
    private void paintTriangle() {
        Random r = new Random();
        float[] color = {r.nextFloat(), r.nextFloat(), r.nextFloat(), 1.0f};
        objectColor.put(color); objectColor.put(color); objectColor.put(color);
    }


    /**
     * Paints triangle in the specified color.
     * @param color
     */
    private void paintTriangle(float[] color) {
        objectColor.put(color); objectColor.put(color); objectColor.put(color);
    }
}