package com.app.letstravel;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewTripDialog {
    private static List<Bitmap> selectedBitmaps = new ArrayList<>();
    private static List<String> imageBase64List = new ArrayList<>();
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_STORAGE_PERMISSION = 200;
    private static final int MAX_IMAGES = 3;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static void show(AppCompatActivity activity, LayoutInflater inflater,
                            ActivityResultLauncher<Intent> cameraLauncher,
                            ActivityResultLauncher<String> galleryLauncher) {

        View dialogView = inflater.inflate(R.layout.dialog_new_trip, null);
        SharedPreferences prefs = activity.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        LinearLayout imagePreviewContainer = dialogView.findViewById(R.id.imagePreviewContainer);

        TextInputLayout subjectLayout = dialogView.findViewById(R.id.subjectLayout);
        TextView tvStartDate = dialogView.findViewById(R.id.tvStartDate);
        TextView tvEndDate = dialogView.findViewById(R.id.tvEndDate);
        TextInputLayout venueLayout = dialogView.findViewById(R.id.venueLayout);
        TextInputLayout activitiesLayout = dialogView.findViewById(R.id.activitiesLayout);
        TextInputLayout participantsLayout = dialogView.findViewById(R.id.participantsLayout);
        TextInputLayout transportationLayout = dialogView.findViewById(R.id.transportationLayout);
        TextView tvDeadline = dialogView.findViewById(R.id.tvDeadline);
        TextInputLayout contactLayout = dialogView.findViewById(R.id.contactLayout);
        TextInputLayout remarkLayout = dialogView.findViewById(R.id.remarkLayout);

        TextInputEditText etSubject = dialogView.findViewById(R.id.etSubject);
        TextInputEditText etVenue = dialogView.findViewById(R.id.etVenue);
        TextInputEditText etActivities = dialogView.findViewById(R.id.etActivities);
        TextInputEditText etParticipants = dialogView.findViewById(R.id.etParticipants);
        TextInputEditText etTransportation = dialogView.findViewById(R.id.etTransportation);
        TextInputEditText etContact = dialogView.findViewById(R.id.etContact);
        TextInputEditText etRemark = dialogView.findViewById(R.id.etRemark);
        Button btnCamera = dialogView.findViewById(R.id.btnTakePhoto);
        Button btnGallery = dialogView.findViewById(R.id.btnSelectPhoto);

        // Clear previous images
        selectedBitmaps.clear();
        imageBase64List.clear();
        imagePreviewContainer.removeAllViews();

        // Date picker listeners with validation
        tvStartDate.setOnClickListener(v -> showStartDatePicker(activity, tvStartDate, tvEndDate, tvDeadline));
        tvEndDate.setOnClickListener(v -> showEndDatePicker(activity, tvStartDate, tvEndDate));
        tvDeadline.setOnClickListener(v -> showDeadlinePicker(activity, tvStartDate, tvDeadline));

        // Camera and Gallery listeners
        btnCamera.setOnClickListener(v -> {
            if (selectedBitmaps.size() >= MAX_IMAGES) {
                Toast.makeText(activity, "Maximum " + MAX_IMAGES + " images allowed", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(intent);
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            }
        });

        btnGallery.setOnClickListener(v -> {
            if (selectedBitmaps.size() >= MAX_IMAGES) {
                Toast.makeText(activity, "Maximum " + MAX_IMAGES + " images allowed", Toast.LENGTH_SHORT).show();
                return;
            }
            String permission = android.os.Build.VERSION.SDK_INT >= 33 ?
                    Manifest.permission.READ_MEDIA_IMAGES :
                    Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(activity, permission)
                    == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                ActivityCompat.requestPermissions(activity,
                        new String[]{permission},
                        REQUEST_STORAGE_PERMISSION);
            }
        });

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                .setTitle("New Trip")
                .setView(dialogView)
                .setPositiveButton("Submit", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button submitButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            submitButton.setOnClickListener(v -> {
                // Clear previous errors
                subjectLayout.setError(null);
                tvStartDate.setError(null);
                tvEndDate.setError(null);
                venueLayout.setError(null);
                activitiesLayout.setError(null);
                participantsLayout.setError(null);
                transportationLayout.setError(null);
                tvDeadline.setError(null);
                contactLayout.setError(null);
                remarkLayout.setError(null);

                // Validate required fields
                boolean isValid = true;
                String subject = etSubject.getText().toString().trim();
                String startDate = tvStartDate.getText().toString();
                String endDate = tvEndDate.getText().toString();
                String venue = etVenue.getText().toString().trim();
                String activities = etActivities.getText().toString().trim();
                String participants = etParticipants.getText().toString().trim();
                String transportation = etTransportation.getText().toString().trim();
                String deadline = tvDeadline.getText().toString();
                String contact = etContact.getText().toString().trim();
                String remark = etRemark.getText().toString().trim();

                if (TextUtils.isEmpty(subject)) {
                    subjectLayout.setError("Enter trip subject");
                    isValid = false;
                }
                if (TextUtils.isEmpty(startDate) || startDate.equals("Select Start Date")) {
                    tvStartDate.setError("Select start date");
                    isValid = false;
                }
                if (TextUtils.isEmpty(endDate) || endDate.equals("Select End Date")) {
                    tvEndDate.setError("Select end date");
                    isValid = false;
                }
                if (TextUtils.isEmpty(venue)) {
                    venueLayout.setError("Enter venue");
                    isValid = false;
                }
                if (TextUtils.isEmpty(activities)) {
                    activitiesLayout.setError("Enter activities");
                    isValid = false;
                }
                if (TextUtils.isEmpty(participants)) {
                    participantsLayout.setError("Enter number of participants");
                    isValid = false;
                }
                if (TextUtils.isEmpty(transportation)) {
                    transportationLayout.setError("Enter transportation");
                    isValid = false;
                }
                if (TextUtils.isEmpty(deadline) || deadline.equals("Select Registration Deadline")) {
                    tvDeadline.setError("Select deadline");
                    isValid = false;
                }
                if (TextUtils.isEmpty(contact)) {
                    contactLayout.setError("Enter contact info");
                    isValid = false;
                }

                // Validate participants number
                int participantCount = 0;
                try {
                    participantCount = Integer.parseInt(participants);
                    if (participantCount <= 0) {
                        participantsLayout.setError("Participants must be greater than 0");
                        isValid = false;
                    }
                } catch (NumberFormatException e) {
                    participantsLayout.setError("Invalid number of participants");
                    isValid = false;
                }

                // Validate date logic
                if (isValid) {
                    try {
                        Calendar today = Calendar.getInstance();
                        today.set(Calendar.HOUR_OF_DAY, 0);
                        today.set(Calendar.MINUTE, 0);
                        today.set(Calendar.SECOND, 0);
                        today.set(Calendar.MILLISECOND, 0);

                        Calendar startCal = Calendar.getInstance();
                        startCal.setTime(DATE_FORMAT.parse(startDate));
                        Calendar endCal = Calendar.getInstance();
                        endCal.setTime(DATE_FORMAT.parse(endDate));
                        Calendar deadlineCal = Calendar.getInstance();
                        deadlineCal.setTime(DATE_FORMAT.parse(deadline));

                        if (startCal.getTimeInMillis() <= today.getTimeInMillis()) {
                            tvStartDate.setError("Start date must be after today");
                            isValid = false;
                        }
                        if (endCal.before(startCal)) {
                            tvEndDate.setError("End date must be on or after start date");
                            isValid = false;
                        }
                        if (!deadlineCal.before(startCal)) {
                            tvDeadline.setError("Deadline must be before start date");
                            isValid = false;
                        }
                    } catch (ParseException e) {
                        Toast.makeText(activity, "Invalid date format", Toast.LENGTH_SHORT).show();
                        isValid = false;
                    }
                }

                if (isValid) {
                    Trip trip = new Trip(
                            subject,
                            startDate,
                            endDate,
                            venue,
                            activities,
                            participantCount,
                            transportation,
                            deadline,
                            contact,
                            remark,
                            new ArrayList<>(imageBase64List),
                            0, // currentParticipants starts at 0
                            participantCount // maxParticipants from user input
                    );
                    trip.setCreatedAt(new Date());
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    db.collection("trips").add(trip)
                            .addOnSuccessListener(docRef -> {
                                Toast.makeText(activity, "Trip submitted", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();

                                // Refresh MainActivity (without biometric recheck)
                                activity.runOnUiThread(() -> {
                                    if (activity instanceof MainActivity) {
                                        ((MainActivity) activity).loadTrips(); // reuse existing method
                                    }
                                });
                            });
                }
            });
        });

        dialog.show();
    }

    public static void handleCameraResult(AppCompatActivity activity, ImageView imgPreview, int resultCode, Intent data, LinearLayout imagePreviewContainer) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");
            addImage(activity, bitmap, imagePreviewContainer);
        }
    }

    public static void handleGalleryResult(AppCompatActivity activity, ImageView imgPreview, Uri uri, LinearLayout imagePreviewContainer) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), uri);
            addImage(activity, bitmap, imagePreviewContainer);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private static void addImage(AppCompatActivity activity, Bitmap bitmap, LinearLayout imagePreviewContainer) {
        if (selectedBitmaps.size() >= MAX_IMAGES) {
            Toast.makeText(activity, "Maximum " + MAX_IMAGES + " images allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedBitmaps.add(bitmap);
        imageBase64List.add(encodeBitmap(bitmap));

        // Create preview view
        LinearLayout previewLayout = new LinearLayout(activity);
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setLayoutParams(new LinearLayout.LayoutParams(
                100, ViewGroup.LayoutParams.WRAP_CONTENT));
        previewLayout.setPadding(8, 8, 8, 8);

        ImageView previewImage = new ImageView(activity);
        previewImage.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 100));
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setImageBitmap(bitmap);

        Button removeButton = new Button(activity);
        removeButton.setText("X");
        removeButton.setTextColor(activity.getResources().getColor(android.R.color.holo_red_dark));
        removeButton.setBackgroundResource(android.R.drawable.btn_default);
        removeButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        removeButton.setOnClickListener(v -> {
            int index = imagePreviewContainer.indexOfChild(previewLayout);
            selectedBitmaps.remove(index);
            imageBase64List.remove(index);
            imagePreviewContainer.removeView(previewLayout);
        });

        previewLayout.addView(previewImage);
        previewLayout.addView(removeButton);
        imagePreviewContainer.addView(previewLayout);
    }

    public static void handlePermissionResult(AppCompatActivity activity, int requestCode,
                                              String[] permissions, int[] grantResults,
                                              ActivityResultLauncher<Intent> cameraLauncher,
                                              ActivityResultLauncher<String> galleryLauncher) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(activity, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                Toast.makeText(activity, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String encodeBitmap(Bitmap bitmap) {
        // Resize image to reduce base64 size
        int targetWidth = 800; // Resize width
        float aspectRatio = (float) bitmap.getHeight() / bitmap.getWidth();
        int targetHeight = Math.round(targetWidth * aspectRatio);

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos); // Compress quality to 50%
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
    }

    private static void showStartDatePicker(Context context, TextView tvStartDate, TextView tvEndDate, TextView tvDeadline) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1); // Tomorrow
        long minDate = calendar.getTimeInMillis();

        DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                (view, year, month, dayOfMonth) -> {
                    String date = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth);
                    tvStartDate.setText(date);
                    tvStartDate.setError(null);
                    // Reset dependent dates if they’re invalid
                    try {
                        Calendar selectedStart = Calendar.getInstance();
                        selectedStart.setTime(DATE_FORMAT.parse(date));
                        Calendar endCal = Calendar.getInstance();
                        if (!tvEndDate.getText().toString().equals("Select End Date")) {
                            endCal.setTime(DATE_FORMAT.parse(tvEndDate.getText().toString()));
                            if (endCal.before(selectedStart)) {
                                tvEndDate.setText("Select End Date");
                            }
                        }
                        Calendar deadlineCal = Calendar.getInstance();
                        if (!tvDeadline.getText().toString().equals("Select Registration Deadline")) {
                            deadlineCal.setTime(DATE_FORMAT.parse(tvDeadline.getText().toString()));
                            if (!deadlineCal.before(selectedStart)) {
                                tvDeadline.setText("Select Registration Deadline");
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(minDate);
        datePickerDialog.show();
    }

    private static void showEndDatePicker(Context context, TextView tvStartDate, TextView tvEndDate) {
        if (tvStartDate.getText().toString().equals("Select Start Date")) {
            Toast.makeText(context, "Please select start date first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Calendar minDateCal = Calendar.getInstance();
            minDateCal.setTime(DATE_FORMAT.parse(tvStartDate.getText().toString()));
            long minDate = minDateCal.getTimeInMillis();

            DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                    (view, year, month, dayOfMonth) -> {
                        String date = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth);
                        tvEndDate.setText(date);
                        tvEndDate.setError(null);
                    }, minDateCal.get(Calendar.YEAR), minDateCal.get(Calendar.MONTH), minDateCal.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.getDatePicker().setMinDate(minDate);
            datePickerDialog.show();
        } catch (ParseException e) {
            Toast.makeText(context, "Invalid start date", Toast.LENGTH_SHORT).show();
        }
    }

    private static void showDeadlinePicker(Context context, TextView tvStartDate, TextView tvDeadline) {
        if (tvStartDate.getText().toString().equals("Select Start Date")) {
            Toast.makeText(context, "Please select start date first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Calendar today = Calendar.getInstance();
            Calendar maxDateCal = Calendar.getInstance();
            maxDateCal.setTime(DATE_FORMAT.parse(tvStartDate.getText().toString()));
            maxDateCal.add(Calendar.DAY_OF_MONTH, -1); // Day before start

            long minDate = today.getTimeInMillis();
            long maxDate = maxDateCal.getTimeInMillis();

            DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                    (view, year, month, dayOfMonth) -> {
                        String date = String.format(Locale.US, "%d-%02d-%02d", year, month + 1, dayOfMonth);
                        tvDeadline.setText(date);
                        tvDeadline.setError(null);
                    }, maxDateCal.get(Calendar.YEAR), maxDateCal.get(Calendar.MONTH), maxDateCal.get(Calendar.DAY_OF_MONTH));

            datePickerDialog.getDatePicker().setMinDate(minDate);
            datePickerDialog.getDatePicker().setMaxDate(maxDate);
            datePickerDialog.show();
        } catch (ParseException e) {
            Toast.makeText(context, "Invalid start date", Toast.LENGTH_SHORT).show();
        }
    }
}