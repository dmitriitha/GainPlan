package com.personal.gainplan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "training_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderScheduler.notificationsAllowed(context)) return;

        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openIntent = PendingIntent.getActivity(context, 17, open, flags);

        String title = intent.getStringExtra("title");
        if (title == null) title = "Сегодня тренировка";

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("GainPlan")
                .setContentText(title)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(title + ". Открой приложение, чтобы запустить таймер подходов."))
                .setContentIntent(openIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(4201, builder.build());

        int day = intent.getIntExtra("calendar_day", -1);
        if (day != -1) ReminderScheduler.rescheduleDay(context, day);
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Напоминания о тренировках",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Уведомления в дни силовых тренировок");
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
