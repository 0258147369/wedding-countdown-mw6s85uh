package com.generated.app;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
//import androidx.core.app.NotificationCompat; // REMOVED

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "WeddingPrefs";
    private static final String KEY_WEDDING_DATE = "weddingDate";
    private static final String KEY_NOTIFICATION_ENABLED = "notificationEnabled";
    private static final String KEY_NOTIFICATION_TYPE = "notificationType"; // "daily" or "milestone"
    private static final String KEY_CUSTOM_IMAGE_URI = "customImageUri";
    private static final String KEY_IMAGE_GALLERY_URIS = "imageGalleryUris"; // Store as JSON string or similar
    private static final String DATABASE_NAME = "wedding_db";
    private static final int DATABASE_VERSION = 1;
    private static final int SELECT_PICTURE_REQUEST_CODE = 1;
    private static final int SELECT_GALLERY_PICTURE_REQUEST_CODE = 2;
    private static final int PICK_IMAGE_REQUEST = 100;
    private static final int PICK_GALLERY_IMAGE_REQUEST = 101;

    private TextView countdownText;
    private TextView dateLabel;
    private EditText dateInput;
    private Button setDateButton;
    private LinearLayout galleryLayout;
    private Button addGalleryImageButton;
    private ImageView mainImageView;
    private Button toggleNotificationsButton;
    private Button setNotificationTypeButton;
    private Button clearDateButton;

    private SharedPreferences preferences;
    private WeddingDBHelper dbHelper;
    private Handler handler = new Handler();
    private Runnable updateCountdownRunnable;

    private String weddingDateString = null;
    private boolean notificationsEnabled = true;
    private String notificationType = "daily"; // "daily", "milestone"
    private String customImageUriString = null;
    private List<String> galleryImageUriStrings = new ArrayList<>();

    private static final String CHANNEL_ID = "wedding_countdown_channel";
    private static final int NOTIFICATION_ID = 1;

    // NotificationCompat was moved from androidx.core.app to android.app.Notification.Builder for API 26+
    // For older APIs, we need to use the v4 support library, which is not allowed per instructions.
    // Therefore, we'll use the framework Notification.Builder and handle compatibility.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        dbHelper = new WeddingDBHelper(this);

        loadPreferences();
        loadGalleryImages();

        setupUI();
        updateCountdown();
        scheduleCountdownUpdates();
        createNotificationChannel();
        updateNotificationButtonState();
        updateNotificationTypeButton();
        updateMainImage();
        loadGalleryImagesFromDb(); // Load and display existing gallery images
    }

    private void loadPreferences() {
        weddingDateString = preferences.getString(KEY_WEDDING_DATE, null);
        notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATION_ENABLED, true);
        notificationType = preferences.getString(KEY_NOTIFICATION_TYPE, "daily");
        customImageUriString = preferences.getString(KEY_CUSTOM_IMAGE_URI, null);
    }

    private void savePreferences() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_WEDDING_DATE, weddingDateString);
        editor.putBoolean(KEY_NOTIFICATION_ENABLED, notificationsEnabled);
        editor.putString(KEY_NOTIFICATION_TYPE, notificationType);
        editor.putString(KEY_CUSTOM_IMAGE_URI, customImageUriString);
        editor.apply();
    }

    private void loadGalleryImages() {
        galleryImageUriStrings.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(WeddingDBHelper.GalleryEntry.TABLE_NAME,
                    new String[]{WeddingDBHelper.GalleryEntry.COLUMN_NAME_URI},
                    null, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int uriColumnIndex = cursor.getColumnIndex(WeddingDBHelper.GalleryEntry.COLUMN_NAME_URI);
                if (uriColumnIndex != -1) {
                    do {
                        galleryImageUriStrings.add(cursor.getString(uriColumnIndex));
                    } while (cursor.moveToNext());
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void saveGalleryImageUri(String uri) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(WeddingDBHelper.GalleryEntry.COLUMN_NAME_URI, uri);
        db.insert(WeddingDBHelper.GalleryEntry.TABLE_NAME, null, values);
    }

    private void removeGalleryImageUri(String uri) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(WeddingDBHelper.GalleryEntry.TABLE_NAME,
                WeddingDBHelper.GalleryEntry.COLUMN_NAME_URI + " = ?",
                new String[]{uri});
    }


    private void setupUI() {
        RelativeLayout mainLayout = new RelativeLayout(this);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5")); // Light gray background
        mainLayout.setPadding(32, 32, 32, 32);

        // Countdown Text
        countdownText = new TextView(this);
        countdownText.setId(View.generateViewId(1)); // Use generateViewId() for API 17+
        countdownText.setTextSize(56);
        countdownText.setTextColor(Color.parseColor("#3F51B5")); // Indigo
        countdownText.setTypeface(null, android.graphics.Typeface.BOLD);
        countdownText.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams countdownParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        countdownParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        countdownParams.setMargins(0, 0, 0, 64);
        mainLayout.addView(countdownText, countdownParams);

        // Date Label
        dateLabel = new TextView(this);
        dateLabel.setId(View.generateViewId(2)); // Use generateViewId() for API 17+
        dateLabel.setText(R.string.wedding_date_label);
        dateLabel.setTextSize(18);
        dateLabel.setTextColor(Color.parseColor("#616161")); // Dark gray
        RelativeLayout.LayoutParams dateLabelParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        dateLabelParams.addRule(RelativeLayout.BELOW, countdownText.getId());
        dateLabelParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        dateLabelParams.setMargins(0, 0, 0, 16);
        mainLayout.addView(dateLabel, dateLabelParams);

        // Date Input
        dateInput = new EditText(this);
        dateInput.setId(View.generateViewId(3)); // Use generateViewId() for API 17+
        dateInput.setHint(R.string.date_input_hint);
        dateInput.setTextSize(18);
        dateInput.setPadding(16, 16, 16, 16);
        dateInput.setBackground(getResources().getDrawable(R.drawable.edit_text_background, null));
        dateInput.setGravity(Gravity.END); // Align text to the end for RTL
        RelativeLayout.LayoutParams dateInputParams = new RelativeLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.edit_text_width), // Define a dimension resource
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        dateInputParams.addRule(RelativeLayout.BELOW, dateLabel.getId());
        dateInputParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        dateInputParams.setMargins(0, 0, 0, 32);
        mainLayout.addView(dateInput, dateInputParams);

        // Set Date Button
        setDateButton = new Button(this);
        setDateButton.setId(View.generateViewId(4)); // Use generateViewId() for API 17+
        setDateButton.setText(R.string.set_date_button);
        setDateButton.setTextSize(16);
        setDateButton.setPadding(32, 16, 32, 16);
        setDateButton.setBackground(getResources().getDrawable(R.drawable.button_background, null));
        setDateButton.setTextColor(Color.WHITE);
        setDateButton.setOnClickListener(v -> setWeddingDate());
        RelativeLayout.LayoutParams setDateButtonParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        setDateButtonParams.addRule(RelativeLayout.BELOW, dateLabel.getId());
        setDateButtonParams.addRule(RelativeLayout.END_OF, dateInput.getId());
        setDateButtonParams.alignWithParent = true; // Ensure it aligns with parent if END_OF is not applicable
        setDateButtonParams.setMargins(16, 0, 0, 32);
        mainLayout.addView(setDateButton, setDateButtonParams);

        // Clear Date Button
        clearDateButton = new Button(this);
        clearDateButton.setId(View.generateViewId(11)); // Use generateViewId() for API 17+
        clearDateButton.setText(R.string.clear_date_button);
        clearDateButton.setTextSize(16);
        clearDateButton.setPadding(32, 16, 32, 16);
        clearDateButton.setBackground(getResources().getDrawable(R.drawable.button_secondary_background, null));
        clearDateButton.setTextColor(getResources().getColor(R.color.button_secondary_text_color, null));
        clearDateButton.setOnClickListener(v -> clearWeddingDate());
        RelativeLayout.LayoutParams clearDateButtonParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        clearDateButtonParams.addRule(RelativeLayout.BELOW, dateInput.getId());
        clearDateButtonParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        clearDateButtonParams.setMargins(0, 16, 0, 64);
        mainLayout.addView(clearDateButton, clearDateButtonParams);


        // Main Image View
        mainImageView = new ImageView(this);
        mainImageView.setId(View.generateViewId(5)); // Use generateViewId() for API 17+
        mainImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mainImageView.setVisibility(View.GONE); // Initially hidden
        mainImageView.setOnClickListener(v -> selectMainImage());
        RelativeLayout.LayoutParams mainImageParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.main_image_height) // Define a dimension resource
        );
        mainImageParams.addRule(RelativeLayout.BELOW, clearDateButton.getId());
        mainImageParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        mainImageParams.setMargins(0, 0, 0, 32);
        mainLayout.addView(mainImageView, mainImageParams);

        // Gallery Label
        TextView galleryLabel = new TextView(this);
        galleryLabel.setId(View.generateViewId(6)); // Use generateViewId() for API 17+
        galleryLabel.setText(R.string.gallery_label);
        galleryLabel.setTextSize(18);
        galleryLabel.setTextColor(Color.parseColor("#616161")); // Dark gray
        RelativeLayout.LayoutParams galleryLabelParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        galleryLabelParams.addRule(RelativeLayout.BELOW, mainImageView.getId());
        galleryLabelParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        galleryLabelParams.setMargins(0, 0, 0, 16);
        mainLayout.addView(galleryLabel, galleryLabelParams);

        // Add Gallery Image Button
        addGalleryImageButton = new Button(this);
        addGalleryImageButton.setId(View.generateViewId(7)); // Use generateViewId() for API 17+
        addGalleryImageButton.setText(R.string.add_gallery_image_button);
        addGalleryImageButton.setTextSize(16);
        addGalleryImageButton.setPadding(32, 16, 32, 16);
        addGalleryImageButton.setBackground(getResources().getDrawable(R.drawable.button_background, null));
        addGalleryImageButton.setTextColor(Color.WHITE);
        addGalleryImageButton.setOnClickListener(v -> openGalleryForGalleryImage());
        RelativeLayout.LayoutParams addGalleryImageButtonParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        addGalleryImageButtonParams.addRule(RelativeLayout.BELOW, mainImageView.getId());
        addGalleryImageButtonParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        addGalleryImageButtonParams.setMargins(0, 0, 0, 32);
        mainLayout.addView(addGalleryImageButton, addGalleryImageButtonParams);

        // Gallery Layout (for displaying images)
        galleryLayout = new LinearLayout(this);
        galleryLayout.setId(View.generateViewId(8)); // Use generateViewId() for API 17+
        galleryLayout.setOrientation(LinearLayout.HORIZONTAL);
        galleryLayout.setGravity(Gravity.CENTER_VERTICAL);
        galleryLayout.setPadding(0, 16, 0, 16);
        // Adjust height to accommodate images and their margins
        int galleryItemSize = getResources().getDimensionPixelSize(R.dimen.gallery_item_size);
        int itemMargin = getResources().getDimensionPixelSize(R.dimen.gallery_item_margin); // Define this in dimens.xml
        RelativeLayout.LayoutParams galleryLayoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                galleryItemSize + 2 * itemMargin // Image size + top/bottom margin
        );
        galleryLayoutParams.addRule(RelativeLayout.BELOW, galleryLabel.getId());
        galleryLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        galleryLayoutParams.setMargins(0, 0, 0, 64);
        mainLayout.addView(galleryLayout, galleryLayoutParams);

        // Toggle Notifications Button
        toggleNotificationsButton = new Button(this);
        toggleNotificationsButton.setId(View.generateViewId(9)); // Use generateViewId() for API 17+
        toggleNotificationsButton.setTextSize(16);
        toggleNotificationsButton.setPadding(32, 16, 32, 16);
        toggleNotificationsButton.setBackground(getResources().getDrawable(R.drawable.button_background, null));
        toggleNotificationsButton.setTextColor(Color.WHITE);
        toggleNotificationsButton.setOnClickListener(v -> toggleNotifications());
        RelativeLayout.LayoutParams toggleNotificationsParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        toggleNotificationsParams.addRule(RelativeLayout.BELOW, galleryLayout.getId());
        toggleNotificationsParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        toggleNotificationsParams.setMargins(0, 0, 0, 16);
        mainLayout.addView(toggleNotificationsButton, toggleNotificationsParams);

        // Set Notification Type Button
        setNotificationTypeButton = new Button(this);
        setNotificationTypeButton.setId(View.generateViewId(10)); // Use generateViewId() for API 17+
        setNotificationTypeButton.setTextSize(16);
        setNotificationTypeButton.setPadding(32, 16, 32, 16);
        setNotificationTypeButton.setBackground(getResources().getDrawable(R.drawable.button_secondary_background, null));
        setNotificationTypeButton.setTextColor(getResources().getColor(R.color.button_secondary_text_color, null));
        setNotificationTypeButton.setOnClickListener(v -> cycleNotificationType());
        RelativeLayout.LayoutParams setNotificationTypeParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        setNotificationTypeParams.addRule(RelativeLayout.BELOW, galleryLayout.getId());
        setNotificationTypeParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        setNotificationTypeParams.setMargins(0, 0, 0, 16);
        mainLayout.addView(setNotificationTypeButton, setNotificationTypeParams);


        setContentView(mainLayout);

        // Dynamically set text and background for buttons based on state
        updateNotificationButtonState();
        updateNotificationTypeButton();

        if (weddingDateString != null) {
            dateInput.setText(weddingDateString);
            dateLabel.setVisibility(View.VISIBLE);
        } else {
            dateLabel.setVisibility(View.GONE);
            dateInput.setHint(R.string.date_input_hint);
        }
    }

    private void updateNotificationButtonState() {
        if (notificationsEnabled) {
            toggleNotificationsButton.setText(R.string.disable_notifications_button);
            toggleNotificationsButton.setBackground(getResources().getDrawable(R.drawable.button_background, null));
            toggleNotificationsButton.setTextColor(Color.WHITE);
        } else {
            toggleNotificationsButton.setText(R.string.enable_notifications_button);
            toggleNotificationsButton.setBackground(getResources().getDrawable(R.drawable.button_secondary_background, null));
            toggleNotificationsButton.setTextColor(getResources().getColor(R.color.button_secondary_text_color, null));
        }
    }

    private void updateNotificationTypeButton() {
        if (notificationType.equals("daily")) {
            setNotificationTypeButton.setText(R.string.notification_type_daily);
            setNotificationTypeButton.setBackground(getResources().getDrawable(R.drawable.button_secondary_background, null));
            setNotificationTypeButton.setTextColor(getResources().getColor(R.color.button_secondary_text_color, null));
        } else { // milestone
            setNotificationTypeButton.setText(R.string.notification_type_milestone);
            setNotificationTypeButton.setBackground(getResources().getDrawable(R.drawable.button_background, null));
            setNotificationTypeButton.setTextColor(Color.WHITE);
        }
    }


    private void setWeddingDate() {
        String dateStr = dateInput.getText().toString().trim();
        if (dateStr.isEmpty()) {
            Toast.makeText(this, R.string.error_date_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date weddingDate = sdf.parse(dateStr);
            if (weddingDate == null) {
                throw new ParseException("Invalid date format", 0);
            }
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            if (weddingDate.before(today.getTime()) || weddingDate.equals(today.getTime())) {
                Toast.makeText(this, R.string.error_date_past_or_today, Toast.LENGTH_SHORT).show();
                return;
            }

            weddingDateString = sdf.format(weddingDate);
            dateLabel.setVisibility(View.VISIBLE);
            dateInput.setText(weddingDateString); // Keep the text in the input field
            savePreferences();
            updateCountdown();
            scheduleNotification(); // Schedule notification based on new date
            Toast.makeText(this, R.string.date_set_success, Toast.LENGTH_SHORT).show();
        } catch (ParseException e) {
            Toast.makeText(this, R.string.error_date_format, Toast.LENGTH_SHORT).show();
        }
    }

    private void clearWeddingDate() {
        weddingDateString = null;
        dateInput.setText("");
        dateLabel.setVisibility(View.GONE);
        countdownText.setText(R.string.default_countdown_text);
        savePreferences();
        cancelNotification();
        Toast.makeText(this, R.string.date_cleared, Toast.LENGTH_SHORT).show();
    }


    private void updateCountdown() {
        if (weddingDateString == null) {
            countdownText.setText(R.string.default_countdown_text);
            dateLabel.setVisibility(View.GONE);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date weddingDate = sdf.parse(weddingDateString);
            if (weddingDate == null) {
                countdownText.setText(R.string.error_invalid_date);
                dateLabel.setVisibility(View.GONE);
                return;
            }

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Date currentDate = today.getTime();

            if (weddingDate.before(currentDate) || weddingDate.equals(currentDate)) {
                countdownText.setText("0");
                dateLabel.setText(R.string.wedding_day_label);
                dateLabel.setVisibility(View.VISIBLE);
                // Handle wedding day UI change (e.g., confetti animation) - placeholder for now
                return;
            }

            long diffInMillis = weddingDate.getTime() - currentDate.getTime();
            long daysUntilWedding = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);

            countdownText.setText(String.valueOf(daysUntilWedding));
            dateLabel.setText(getString(R.string.days_left_label, weddingDateString));
            dateLabel.setVisibility(View.VISIBLE);

        } catch (ParseException e) {
            countdownText.setText(R.string.error_parsing_date);
            dateLabel.setVisibility(View.GONE);
        }
    }

    private void scheduleCountdownUpdates() {
        updateCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                updateCountdown();
                // Schedule the next update for tomorrow morning
                Calendar nextMidnight = Calendar.getInstance();
                nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
                nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
                nextMidnight.set(Calendar.MINUTE, 0);
                nextMidnight.set(Calendar.SECOND, 0);
                long delay = nextMidnight.getTimeInMillis() - System.currentTimeMillis();
                if (delay < 0) delay = 0; // Ensure delay is not negative
                handler.postAtTime(this, System.currentTimeMillis() + delay);
            }
        };
        handler.post(updateCountdownRunnable);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void scheduleNotification() {
        if (!notificationsEnabled || weddingDateString == null) {
            cancelNotification();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Date weddingDate = sdf.parse(weddingDateString);
            if (weddingDate == null) return;

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            long diffInMillis = weddingDate.getTime() - today.getTimeInMillis();
            long daysUntilWedding = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);

            // For simplicity, we'll send a notification immediately if it's a milestone or daily reminder time.
            // A robust solution would use AlarmManager for background scheduling.

            if (notificationType.equals("daily")) {
                // For daily, send a reminder if the current time is suitable (e.g., after 8 AM)
                Calendar now = Calendar.getInstance();
                if (now.get(Calendar.HOUR_OF_DAY) >= 8) {
                     sendNotification("תזכורת יומית:", "היום המרגש מתקרב!");
                }
            } else { // milestone
                // Schedule for specific milestones (e.g., 1 week, 1 month before, day before, wedding day)
                if (daysUntilWedding == 7 || daysUntilWedding == 30 || daysUntilWedding == 1 || daysUntilWedding == 0) {
                    String message = "נשארו " + (daysUntilWedding == 0 ? "רק " : "") + daysUntilWedding + " ימים לחתונה!";
                    if (daysUntilWedding == 0) message = "היום החתונה!";
                    sendNotification(message, "יום מיוחד מתקרב!");
                } else if (daysUntilWedding < 0) {
                    // Wedding has passed, cancel notifications
                    cancelNotification();
                }
            }

        } catch (ParseException e) {
            Log.e("MainActivity", "Error parsing wedding date for notification scheduling", e);
        }
    }

    // Using the framework Notification.Builder directly
    private void sendNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder = new android.app.Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(android.app.Notification.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void toggleNotifications() {
        notificationsEnabled = !notificationsEnabled;
        savePreferences();
        updateNotificationButtonState();
        if (notificationsEnabled) {
            scheduleNotification();
        } else {
            cancelNotification();
        }
        Toast.makeText(this, notificationsEnabled ? R.string.notifications_enabled : R.string.notifications_disabled, Toast.LENGTH_SHORT).show();
    }

    private void cycleNotificationType() {
        if (notificationType.equals("daily")) {
            notificationType = "milestone";
        } else {
            notificationType = "daily";
        }
        savePreferences();
        updateNotificationTypeButton();
        scheduleNotification(); // Reschedule based on new type
        Toast.makeText(this, notificationType.equals("daily") ? R.string.notification_type_set_daily : R.string.notification_type_set_milestone, Toast.LENGTH_SHORT).show();
    }

    private void selectMainImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void openGalleryForGalleryImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_GALLERY_IMAGE_REQUEST);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                customImageUriString = selectedImageUri.toString();
                savePreferences();
                updateMainImage();
            } else if (requestCode == PICK_GALLERY_IMAGE_REQUEST && data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                String uriString = selectedImageUri.toString();
                if (!galleryImageUriStrings.contains(uriString)) {
                    galleryImageUriStrings.add(uriString);
                    saveGalleryImageUri(uriString);
                    addGalleryImageView(selectedImageUri);
                } else {
                    Toast.makeText(this, R.string.image_already_in_gallery, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void updateMainImage() {
        if (customImageUriString != null) {
            try {
                Uri imageUri = Uri.parse(customImageUriString);
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                if (bitmap != null) {
                    mainImageView.setImageBitmap(bitmap);
                    mainImageView.setVisibility(View.VISIBLE);
                } else {
                    mainImageView.setVisibility(View.GONE);
                }
            } catch (IOException e) {
                Log.e("MainActivity", "Error loading main image", e);
                mainImageView.setVisibility(View.GONE);
            } catch (SecurityException e) {
                Log.e("MainActivity", "Permission error loading main image", e);
                mainImageView.setVisibility(View.GONE);
            }
        } else {
            mainImageView.setVisibility(View.GONE);
        }
    }

    private void addGalleryImageView(Uri imageUri) {
        ImageView imageView = new ImageView(this);
        int galleryItemSize = getResources().getDimensionPixelSize(R.dimen.gallery_item_size);
        int itemMargin = getResources().getDimensionPixelSize(R.dimen.gallery_item_margin); // Define this in dimens.xml
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                galleryItemSize,
                galleryItemSize
        );
        params.setMargins(itemMargin, 0, itemMargin, 0); // Apply margin to left and right
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageURI(imageUri);

        // Add long click listener to remove image
        imageView.setOnLongClickListener(v -> {
            removeGalleryImage(imageUri.toString());
            galleryLayout.removeView(v);
            Toast.makeText(this, R.string.image_removed, Toast.LENGTH_SHORT).show();
            return true;
        });

        galleryLayout.addView(imageView);
    }

    private void loadGalleryImagesFromDb() {
        galleryLayout.removeAllViews(); // Clear existing views before loading
        for (String uriString : galleryImageUriStrings) {
            try {
                Uri imageUri = Uri.parse(uriString);
                addGalleryImageView(imageUri);
            } catch (Exception e) {
                Log.e("MainActivity", "Error loading gallery image from URI: " + uriString, e);
                // Optionally remove invalid URI from DB here
                removeGalleryImageUri(uriString); // Remove problematic URI
            }
        }
    }

    private void removeGalleryImage(String uriToRemove) {
        galleryImageUriStrings.remove(uriToRemove);
        removeGalleryImageUri(uriToRemove);
        // savePreferences(); // No need to save prefs here, DB write handles persistence
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateCountdownRunnable);
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
