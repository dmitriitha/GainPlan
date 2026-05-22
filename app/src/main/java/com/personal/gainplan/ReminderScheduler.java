package com.personal.gainplan;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Calendar;

final class ReminderScheduler {
    static final String PREFS = "gain_plan_prefs";
    static final String KEY_ENABLED = "reminders_enabled";
    static final String KEY_HOUR = "reminder_hour";
    static final String KEY_MINUTE = "reminder_minute";
    private static final int[] TRAINING_DAYS = {
            Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.SATURDAY
    };

    private ReminderScheduler() {
    }

    static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean exactAlarmsAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    static Intent exactAlarmSettingsIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) scheduleAll(context);
        else cancelAll(context);
    }

    static void setTime(Context context, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
        if (prefs(context).getBoolean(KEY_ENABLED, false)) scheduleAll(context);
    }

    static int hour(Context context) {
        return prefs(context).getInt(KEY_HOUR, 10);
    }

    static int minute(Context context) {
        return prefs(context).getInt(KEY_MINUTE, 0);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void scheduleAll(Context context) {
        if (!exactAlarmsAllowed(context)) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        cancelAll(context);
        int hour = hour(context);
        int minute = minute(context);
        for (int day : TRAINING_DAYS) {
            Calendar next = nextOccurrence(day, hour, minute);
            PendingIntent intent = pendingIntent(context, day);
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), intent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), intent);
            }
        }
    }

    static void rescheduleDay(Context context, int calendarDay) {
        if (!enabled(context) || !exactAlarmsAllowed(context)) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Calendar next = nextOccurrence(calendarDay, hour(context), minute(context));
        next.add(Calendar.DAY_OF_YEAR, 7);
        PendingIntent intent = pendingIntent(context, calendarDay);
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), intent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), intent);
        }
    }

    static void cancelAll(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        for (int day : TRAINING_DAYS) {
            alarmManager.cancel(pendingIntent(context, day));
        }
    }

    private static PendingIntent pendingIntent(Context context, int calendarDay) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("calendar_day", calendarDay);
        intent.putExtra("title", titleForDay(calendarDay));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 9000 + calendarDay, intent, flags);
    }

    private static Calendar nextOccurrence(int calendarDay, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        int today = calendar.get(Calendar.DAY_OF_WEEK);
        int daysAhead = calendarDay - today;
        if (daysAhead < 0 || (daysAhead == 0 && calendar.before(Calendar.getInstance()))) {
            daysAhead += 7;
        }
        calendar.add(Calendar.DAY_OF_YEAR, daysAhead);
        return calendar;
    }

    private static String titleForDay(int calendarDay) {
        if (calendarDay == Calendar.MONDAY) return "Силовая A: грудь, бицепс, пресс";
        if (calendarDay == Calendar.WEDNESDAY) return "Силовая B: ноги, спина, пресс";
        return "Силовая C: верх тела, руки, корпус";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
