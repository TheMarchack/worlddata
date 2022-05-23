package com.example.worlddata;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MainActivity extends AppCompatActivity {

    private OpenGLView openGLView;
    private static MainActivity instance;
    private TextView debugText;

    private static final int PICKFILE_REQUEST_CODE = 8777;

    /**
     * Called when the activity is starting.
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        openGLView = (OpenGLView) findViewById(R.id.openGLView);
        debugText = (TextView) findViewById(R.id.debugText);
        MaterialButton loadBtn = (MaterialButton) findViewById(R.id.loadBtn);
        MaterialButton swapTexture = (MaterialButton) findViewById(R.id.swapTexture);

        // Check if the system supports OpenGL ES 2.0.
        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000;

        loadBtn.setOnClickListener(new View.OnClickListener() {
            /**
             * Open file explorer to load a file. If permission not granted, asks for it.
             * @param v
             */
            @Override
            public void onClick(View v) {
                enableAllFilesAccess();
                if ((SDK_INT >= 30 && Environment.isExternalStorageManager()) || checkPermission()) {
                    // Permission granted
                    Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                    chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
                    chooseFile.setType("*/*");
                    startActivityForResult(Intent.createChooser(chooseFile, "Choose a file"), PICKFILE_REQUEST_CODE);
                } else {
                    // Permission denied
                    requestPermission();
                }
            }
        });

        swapTexture.setOnClickListener(new View.OnClickListener() {
            /**
             * Calls the change of the world texture from geographic to land mass shape.
             * @param v
             */
            @Override
            public void onClick(View v) {
                openGLView.swapTexture();
            }
        });
    }

    /**
     * Used to get the instance of main activity.
     * @return Instance object.
     */
    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onResume() {
        super.onResume();
        openGLView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        openGLView.onPause();
    }

    /**
     * Loads GeoJSON file contents and sends them to interpretation and display.
     * @param requestCode request code defined in the beginning of class file.
     * @param resultCode result code must be RESULT_OK to proceed.
     * @param data Intent object must be ACTION_GET_CONTENT.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri content_describer = data.getData();
            BufferedReader reader = null;
            try {
                // Open the user-picked file for reading:
                InputStream in = getContentResolver().openInputStream(content_describer);
                // Read the content:
                reader = new BufferedReader(new InputStreamReader(in));
                String line;
                StringBuilder builder = new StringBuilder();
                while ((line = reader.readLine()) != null){
                    builder.append(line);
                }
                // Interpret opened file data
                interpretGeoJson(new Gson().fromJson(builder.toString(), JsonObject.class));
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Checks the GeoJSON data to see if point data contains categories. Visualizes them accordingly.
     * @param gson JsonObject of GeoJSON data features.
     */
    private void interpretGeoJson(JsonObject gson) {
        try {
            // Get necessary data from GeoJSON
            JsonArray geometry = gson.getAsJsonArray("features");
            debugText.setText(String.valueOf(gson.get("title").getAsString()));
            if (geometry.get(0).getAsJsonObject().get("properties").getAsJsonObject().keySet().contains("category")) {
                Log.i("Interpretation", "Found that properties contain - category.");
                openGLView.drawCategories(geometry);
            } else {
                Log.i("Interpretation", "Did not find property - category.");
                openGLView.drawPoints(geometry);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Could not process selected file.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Checks app's permission to read external storage.
     * @return boolean value for permission check.
     */
    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    /** Requests app's permission to read external storage. */
    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Toast.makeText(MainActivity.this, "Storage access required. Enable in settings.", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 111);
        }
    }

    /** Enables all file access to see unrecognized by Android file types, like GeoJSON. */
    private void enableAllFilesAccess() {
        if(SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                startActivity(intent);
            }
        }
    }

    /**
     * Displays certain text in bottom area of app's screen.
     * @param text string to be displayed.
     */
    public void setText(String text) {
        debugText.setText(text);
    }
}
