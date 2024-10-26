package com.example.myapplication;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<String> items;
    private HashMap<String, String> videoPathMap; // Store video paths
    private ArrayAdapter<String> adapter;
    private Button recordButton;
    private int nextVideoNumber = 1;
    private Uri videoUri;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean allGranted = true;
                        for (Boolean isGranted : permissions.values()) {
                            allGranted &= isGranted;
                        }
                        if (allGranted) {
                            startVideoRecording();
                        } else {
                            Toast.makeText(this, "Camera and storage permissions are required",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<Uri> videoCaptureLauncher = registerForActivityResult(
            new ActivityResultContracts.CaptureVideo(),
            success -> {
                if (success && videoUri != null) {
                    handleVideoRecorded(videoUri);
                } else {
                    Toast.makeText(this, "Failed to record video",
                            Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);
        recordButton = findViewById(R.id.recordButton);

        items = new ArrayList<>();
        videoPathMap = new HashMap<>();

        // Add default videos if they exist in raw resources
        addItem("Yoga 1", "android.resource://" + getPackageName() + "/" + R.raw.video1);
        addItem("Yoga 2", "android.resource://" + getPackageName() + "/" + R.raw.video2);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = items.get(position);
            String videoPath = videoPathMap.get(selectedItem);

            if (videoPath != null) {
                Intent intent = new Intent(MainActivity.this, MainActivity2.class);
                intent.putExtra("VIDEO_URI", videoPath);
                startActivity(intent);
            }
        });

        recordButton.setOnClickListener(v -> checkPermissionsAndRecord());

        // Load saved video paths
        loadSavedVideos();
    }

    private void checkPermissionsAndRecord() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (permissionsNeeded.isEmpty()) {
            startVideoRecording();
        } else {
            permissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        }
    }

    private void startVideoRecording() {
        String fileName = "yoga_video_" + nextVideoNumber + ".mp4";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, fileName);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/YogaApp");
        }

        videoUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        if (videoUri != null) {
            videoCaptureLauncher.launch(videoUri);
        } else {
            Toast.makeText(this, "Error creating video file",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void handleVideoRecorded(Uri videoUri) {
        try {
            String videoFileName = "yoga_video_" + nextVideoNumber + ".mp4";
            File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "YogaApp");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File(outputDir, videoFileName);

            // Copy the video file
            copyVideo(videoUri, outputFile);

            // Verify the file was created and has content
            if (outputFile.exists() && outputFile.length() > 0) {
                String videoPath = outputFile.getAbsolutePath();
                String itemName = "Yoga " + nextVideoNumber; // Use nextVideoNumber for item name

                // Add new yoga item to the list with its file path
                addItem(itemName, videoPath);
                adapter.notifyDataSetChanged();

                Toast.makeText(this, "Video saved successfully",
                        Toast.LENGTH_SHORT).show();

                // Increment nextVideoNumber only after a successful save
                nextVideoNumber++;

                // Save the video path to SharedPreferences
                SharedPreferences sharedPreferences = getSharedPreferences("VideoPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("video_" + (nextVideoNumber - 1), videoPath); // Store with the correct index
                editor.putInt("video_count", nextVideoNumber - 1); // Update count correctly
                editor.apply();
            } else {
                Toast.makeText(this, "Error: Video file is empty or not created",
                        Toast.LENGTH_LONG).show();
            }

        } catch (IOException e) {
            Toast.makeText(this, "Error saving video: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void copyVideo(Uri sourceUri, File destFile) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {

            if (in == null) {
                throw new IOException("Could not open input stream");
            }

            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
    }

    private void addItem(String item, String videoPath) {
        items.add(item);
        videoPathMap.put(item, videoPath);
    }

    private void loadSavedVideos() {
        SharedPreferences sharedPreferences = getSharedPreferences("VideoPrefs", MODE_PRIVATE);
        int count = sharedPreferences.getInt("video_count", 0);
        for (int i = 1; i <= count; i++) {
            String videoPath = sharedPreferences.getString("video_" + i, null);
            if (videoPath != null) {
                addItem("Yoga " + i, videoPath);
            }
        }
    }
}
