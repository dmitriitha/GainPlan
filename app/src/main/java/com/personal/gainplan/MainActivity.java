package com.personal.gainplan;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends android.app.Activity {
    private static final int BG = Color.rgb(247, 248, 244);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(23, 27, 31);
    private static final int MUTED = Color.rgb(101, 111, 118);
    private static final int LINE = Color.rgb(226, 229, 222);
    private static final int GREEN = Color.rgb(20, 132, 102);
    private static final int DEEP = Color.rgb(12, 84, 70);
    private static final int CORAL = Color.rgb(229, 99, 72);
    private static final int SOFT_GREEN = Color.rgb(225, 244, 236);
    private static final int BLUE = Color.rgb(83, 160, 214);
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MIGRATION_VERSION = 6;

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout bottomBar;
    private String currentTab = "home";
    private long lastBackPressMs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private int secondsLeft;
    private boolean timerRunning;
    private boolean restMode;

    private TrainingData.DayPlan activePlan;
    private final Map<String, TrainingData.Exercise> selected = new HashMap<>();
    private final List<TrainingData.Exercise> activeWorkout = new ArrayList<>();
    private int activeExerciseIndex;
    private int activeSet = 1;
    private TextView timerMode;
    private TextView timerClock;
    private ProgressBar timerProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        ensurePlanStartDate();
        buildShell();
        showHome();
    }

    @Override
    public void onBackPressed() {
        stopTimer();
        if (!"home".equals(currentTab)) {
            showHome();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 1800) {
            super.onBackPressed();
        } else {
            lastBackPressMs = now;
            Toast.makeText(this, "Нажмите назад еще раз для выхода", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, statusBarHeight(), 0, 0);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(18));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(8), dp(8), dp(8), dp(10));
        bottomBar.setBackground(round(SURFACE, 0, 0, 0));
        root.addView(bottomBar, new LinearLayout.LayoutParams(-1, dp(74)));
        setContentView(root);
    }

    private void renderBottom(String active) {
        currentTab = active;
        bottomBar.removeAllViews();
        bottomBar.addView(nav("Главная", active.equals("home"), v -> showHome()));
        bottomBar.addView(nav("Упр.", active.equals("library"), v -> showExerciseLibrary()));
        bottomBar.addView(nav("Питание", active.equals("nutrition"), v -> showNutrition()));
        bottomBar.addView(nav("Прогресс", active.equals("progress"), v -> showProgress()));
    }

    private Button nav(String label, boolean active, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(active ? Color.WHITE : MUTED);
        button.setBackground(round(active ? DEEP : Color.TRANSPARENT, active ? DEEP : Color.TRANSPARENT, 18, 0));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void clear(String tab) {
        stopTimer();
        content.removeAllViews();
        renderBottom(tab);
        content.setAlpha(0f);
        content.setTranslationY(dp(10));
        content.animate().alpha(1f).translationY(0).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void showHome() {
        clear("home");
        TrainingData.DayPlan today = todayPlan();
        int week = currentWeek();
        String status = statusFor(today);
        boolean completedToday = today.isWorkout() && hasCompletedDate(isoDate(Calendar.getInstance()));

        LinearLayout hero = card(DEEP);
        hero.addView(text("GainPlan", 14, true, Color.rgb(177, 228, 207)));
        hero.addView(text("Неделя " + week + " из 12", 26, true, Color.WHITE));
        hero.addView(text(today.day + " • " + status + "\n" + today.title + " • " + today.focus, 15, false, Color.WHITE));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(12);
        bar.setProgress(week);
        hero.addView(bar, new LinearLayout.LayoutParams(-1, dp(10)));
        content.addView(hero);

        if (prefs().getBoolean("pending_workout_finish", false)) {
            Button finish = largeRoundButton("Завершить тренировку", CORAL);
            finish.setOnClickListener(v -> finishPendingWorkout());
            content.addView(finish);
        } else {
            String label = completedToday ? "Тренировка выполнена" : (today.isWorkout() ? "Начать тренировку" : status);
            Button primary = largeRoundButton(label, today.isWorkout() && !completedToday ? GREEN : LINE);
            primary.setEnabled(today.isWorkout() && !completedToday);
            primary.setTextColor(today.isWorkout() && !completedToday ? Color.WHITE : MUTED);
            primary.setOnClickListener(v -> showWorkoutBuilder(today));
            content.addView(primary);
        }

        addReminderMini();
        LinearLayout phase = card(SURFACE);
        phase.addView(text("Фаза плана", 18, true, INK));
        phase.addView(text(TrainingData.phaseForWeek(week), 15, false, MUTED));
        content.addView(phase);
    }

    private void addReminderMini() {
        LinearLayout box = card(SURFACE);
        box.addView(text("Напоминания", 18, true, INK));
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Силовые дни в " + two(ReminderScheduler.hour(this)) + ":" + two(ReminderScheduler.minute(this)));
        enabled.setTextColor(INK);
        enabled.setTextSize(15);
        enabled.setChecked(ReminderScheduler.enabled(this));
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            requestNotificationPermissionsIfNeeded();
            if (isChecked && !ReminderScheduler.exactAlarmsAllowed(this)) openExactAlarmSettings();
            ReminderScheduler.setEnabled(this, isChecked);
        });
        Button time = ghostButton("Выбрать время");
        time.setOnClickListener(v -> new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            ReminderScheduler.setTime(this, hourOfDay, minute);
            showHome();
        }, ReminderScheduler.hour(this), ReminderScheduler.minute(this), true).show());
        box.addView(enabled);
        box.addView(time);
        content.addView(box);
    }

    private void showWorkoutBuilder(TrainingData.DayPlan plan) {
        clear("home");
        activePlan = plan;
        selected.clear();
        activeWorkout.clear();
        renderBuilder(plan);
    }

    private void renderBuilder(TrainingData.DayPlan plan) {
        content.removeAllViews();
        addHeader("Конструктор", plan.title + " • выбери 1 упражнение на каждую группу");
        for (String group : TrainingData.muscleGroupsFor(plan)) {
            LinearLayout box = card(SURFACE);
            box.addView(text(group, 20, true, INK));
            for (TrainingData.Exercise exercise : TrainingData.choicesForGroup(group)) {
                TrainingData.Exercise chosen = selected.get(group);
                boolean active = chosen != null && chosen.name.equals(exercise.name);
                Button option = choiceButton(exercise.name + "\n" + exercise.target, active);
                option.setOnClickListener(v -> {
                    selected.put(group, exercise);
                    renderBuilder(plan);
                });
                box.addView(option);
            }
            content.addView(box);
        }
        Button start = primaryButton("Начать");
        boolean ready = selected.size() == TrainingData.muscleGroupsFor(plan).size();
        start.setEnabled(ready);
        if (!ready) {
            start.setTextColor(MUTED);
            start.setBackground(round(LINE, LINE, 22, 0));
        }
        start.setOnClickListener(v -> startConstructedWorkout(plan));
        content.addView(start);
    }

    private void startConstructedWorkout(TrainingData.DayPlan plan) {
        activePlan = plan;
        activeWorkout.clear();
        for (String group : TrainingData.muscleGroupsFor(plan)) activeWorkout.add(selected.get(group));
        activeExerciseIndex = 0;
        activeSet = 1;
        showCurrentExercise();
    }

    private void showCurrentExercise() {
        clear("home");
        if (activeWorkout.isEmpty()) {
            showHome();
            return;
        }
        TrainingData.Exercise exercise = activeWorkout.get(activeExerciseIndex);
        secondsLeft = exercise.workSeconds;
        restMode = false;

        addHeader("Упражнение " + (activeExerciseIndex + 1) + " из " + activeWorkout.size(), exercise.name);

        LinearLayout timer = card(SURFACE);
        timerMode = text("Подход " + activeSet + " • работа", 15, true, GREEN);
        timerClock = text(format(secondsLeft), 52, true, INK);
        timerClock.setGravity(Gravity.CENTER);
        timerProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        timerProgress.setMax(secondsLeft);
        timerProgress.setProgress(secondsLeft);
        timer.addView(timerMode);
        timer.addView(timerClock);
        timer.addView(timerProgress, new LinearLayout.LayoutParams(-1, dp(10)));
        LinearLayout controls = row();
        Button work = compactButton("Старт");
        Button rest = compactButton("Отдых");
        Button next = compactButton(activeExerciseIndex == activeWorkout.size() - 1 ? "На главную" : "Дальше");
        work.setOnClickListener(v -> startTimer(exercise.workSeconds, false));
        rest.setOnClickListener(v -> startTimer(exercise.restSeconds, true));
        next.setOnClickListener(v -> nextExercise());
        controls.addView(work);
        controls.addView(rest);
        controls.addView(next);
        timer.addView(controls);
        content.addView(timer);

        LinearLayout info = card(SURFACE);
        info.addView(new ExerciseAnimationView(this, exercise.name), new LinearLayout.LayoutParams(-1, exerciseImageHeight(exercise.name, 180)));
        info.addView(text(exercise.target, 17, true, INK));
        info.addView(text("Старт: " + exercise.setup, 15, false, MUTED));
        info.addView(text("Движение: " + exercise.movement, 15, false, MUTED));
        info.addView(text("Ошибки: " + exercise.mistakes, 15, false, MUTED));
        info.addView(text("На массу: " + exercise.progression, 15, false, MUTED));
        content.addView(info);
    }

    private void nextExercise() {
        stopTimer();
        activeExerciseIndex++;
        activeSet = 1;
        if (activeExerciseIndex >= activeWorkout.size()) {
            prefs().edit()
                    .putBoolean("pending_workout_finish", true)
                    .putString("pending_workout_title", activePlan.title)
                    .putInt("pending_workout_count", activeWorkout.size())
                    .apply();
            Toast.makeText(this, "Нажми «Завершить тренировку» на главном экране", Toast.LENGTH_LONG).show();
            showHome();
        } else {
            showCurrentExercise();
        }
    }

    private void showExerciseLibrary() {
        clear("library");
        addHeader("Упражнения", "Техника, ошибки и короткая анимация движения");
        for (TrainingData.Exercise exercise : TrainingData.library()) {
            LinearLayout box = card(SURFACE);
            box.addView(text(exercise.name, 18, true, INK));
            box.addView(text(exercise.muscle + " • " + exercise.target, 14, false, MUTED));
            Button open = ghostButton("Открыть");
            open.setOnClickListener(v -> showExerciseDetail(exercise));
            box.addView(open);
            content.addView(box);
        }
    }

    private void showExerciseDetail(TrainingData.Exercise exercise) {
        clear("library");
        addHeader(exercise.name, exercise.muscle + " • " + exercise.target);
        LinearLayout box = card(SURFACE);
        box.addView(new ExerciseAnimationView(this, exercise.name), new LinearLayout.LayoutParams(-1, exerciseImageHeight(exercise.name, 210)));
        box.addView(text("Как делать", 20, true, INK));
        box.addView(text("Старт: " + exercise.setup, 15, false, MUTED));
        box.addView(text("Движение: " + exercise.movement, 15, false, MUTED));
        box.addView(text("Частые ошибки: " + exercise.mistakes, 15, false, MUTED));
        box.addView(text("Прогрессия: " + exercise.progression, 15, false, MUTED));
        box.addView(text("Замены: " + join(exercise.alternatives), 15, false, MUTED));
        content.addView(box);
        Button back = primaryButton("Назад к списку");
        back.setOnClickListener(v -> showExerciseLibrary());
        content.addView(back);
    }

    private void showNutrition() {
        clear("nutrition");
        addHeader("Питание", "Ориентир для набора массы при твоих тренировках");
        LinearLayout kcal = card(DEEP);
        kcal.addView(text("3300 ккал в день", 28, true, Color.WHITE));
        kcal.addView(text("Белок 130-160 г • креатин 3-5 г • вода 2.5-3.5 л", 15, false, Color.WHITE));
        content.addView(kcal);

        addMeal("Завтрак", "3 яйца, 80-100 г овсянки или гречки, банан, 200 г творога или йогурта. Если не лезет еда: часть овсянки можно заменить коктейлем.");
        addMeal("Обед", "180-220 г курицы/говядины/рыбы, 100-130 г сухого риса/пасты/гречки, 250-300 г овощей, 1-2 ложки оливкового масла.");
        addMeal("Перекус", "Протеин 25-30 г белка, молоко или кефир, банан. Альтернатива: творог 200 г + мед/ягоды + хлеб.");
        addMeal("Ужин", "180-220 г мяса/рыбы/творога/бобовых, картофель/рис/паста, овощи. Перед сном при недоборе: творог 150-200 г.");
        addMeal("Коррекция", "Если вес не растет 2 недели подряд, добавь 200-300 ккал: орехи 30 г, бутерброд, дополнительная крупа или коктейль с молоком.");
    }

    private void addMeal(String title, String body) {
        LinearLayout box = card(SURFACE);
        box.addView(text(title, 19, true, INK));
        box.addView(text(body, 15, false, MUTED));
        content.addView(box);
    }

    private void showProgress() {
        clear("progress");
        addHeader("Прогресс", "Вес по дням и выполненные силовые");
        addBodyWeightForm();
        LinearLayout weight = card(SURFACE);
        weight.addView(text("Вес по дням", 18, true, INK));
        weight.addView(new WeightChartView(this, prefs().getString("weight_entries", "")), new LinearLayout.LayoutParams(-1, dp(220)));
        content.addView(weight);

        LinearLayout streak = card(SURFACE);
        streak.addView(text("Силовые тренировки", 18, true, INK));
        streak.addView(text("Зеленый — силовая выполнена, серый — силовая пропущена, голубой — спорт, белый — отдых.", 14, false, MUTED));
        streak.addView(new StreakChartView(this, prefs().getString("completed_dates", ""), planStartMillis()), new LinearLayout.LayoutParams(-1, dp(360)));
        content.addView(streak);
    }

    private void addBodyWeightForm() {
        LinearLayout box = card(SURFACE);
        box.addView(text("Новый замер", 18, true, INK));
        String today = isoDate(Calendar.getInstance());
        boolean already = hasEntryForToday();
        EditText weight = input("", already ? "Сегодня уже записано" : "Вес, кг");
        weight.setEnabled(!already);
        box.addView(label("Вес"));
        box.addView(weight);
        Button save = primaryButton(already ? "Вес уже записан сегодня" : "Сохранить вес");
        save.setEnabled(!already);
        if (already) {
            save.setTextColor(MUTED);
            save.setBackground(round(LINE, LINE, 22, 0));
        }
        save.setOnClickListener(v -> {
            String value = value(weight);
            if ("-".equals(value)) return;
            appendUniqueWeight(today, value);
            try {
                prefs().edit().putInt("weight", Math.round(Float.parseFloat(value.replace(",", ".")))).apply();
            } catch (Exception ignored) {
            }
            showProgress();
        });
        box.addView(save);
        content.addView(box);
    }

    private void finishPendingWorkout() {
        String title = prefs().getString("pending_workout_title", "Силовая");
        int count = prefs().getInt("pending_workout_count", 0);
        append("workout_log", date() + " • " + title + " • " + count + " упражнения");
        addCompletedDate(isoDate(Calendar.getInstance()));
        prefs().edit().putBoolean("pending_workout_finish", false).apply();
        Toast.makeText(this, "Тренировка засчитана", Toast.LENGTH_SHORT).show();
        showHome();
    }

    private void startTimer(int seconds, boolean rest) {
        stopTimer();
        secondsLeft = seconds;
        restMode = rest;
        timerRunning = true;
        timerProgress.setMax(seconds);
        tick();
    }

    private void tick() {
        updateTimerLabels();
        ticker = () -> {
            if (!timerRunning) return;
            secondsLeft--;
            if (secondsLeft <= 0) {
                timerRunning = false;
                if (restMode) activeSet++;
                Toast.makeText(this, restMode ? "Отдых закончен" : "Подход закончен", Toast.LENGTH_SHORT).show();
                updateTimerLabels();
                return;
            }
            tick();
        };
        handler.postDelayed(ticker, 1000);
    }

    private void updateTimerLabels() {
        if (timerClock == null || timerMode == null || timerProgress == null) return;
        timerClock.setText(format(secondsLeft));
        timerMode.setText("Подход " + activeSet + " • " + (restMode ? "отдых" : "работа"));
        timerMode.setTextColor(restMode ? CORAL : GREEN);
        timerProgress.setProgress(Math.max(secondsLeft, 0));
    }

    private void stopTimer() {
        timerRunning = false;
        if (ticker != null) handler.removeCallbacks(ticker);
        ticker = null;
    }

    private void addHeader(String title, String subtitle) {
        TextView t = text(title, 28, true, INK);
        TextView s = text(subtitle, 15, false, MUTED);
        t.setPadding(0, dp(4), 0, 0);
        s.setPadding(0, 0, 0, dp(10));
        content.addView(t);
        content.addView(s);
    }

    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(round(color, color == SURFACE ? LINE : color, 24, color == SURFACE ? 1 : 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(params);
        return box;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private Button largeRoundButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(24);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(color, color, 120, 0));
        int height = Math.max(dp(190), getResources().getDisplayMetrics().heightPixels / 3);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.setMargins(0, dp(4), 0, dp(12));
        button.setLayoutParams(params);
        return button;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackground(round(GREEN, GREEN, 22, 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.setMargins(0, dp(8), 0, dp(12));
        button.setLayoutParams(params);
        return button;
    }

    private Button ghostButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(DEEP);
        button.setBackground(round(SOFT_GREEN, SOFT_GREEN, 18, 0));
        return button;
    }

    private Button compactButton(String label) {
        Button button = ghostButton(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(4), dp(10), dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button choiceButton(String label, boolean active) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextSize(15);
        button.setTextColor(active ? Color.WHITE : INK);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(round(active ? Color.rgb(53, 88, 80) : BG, active ? DEEP : LINE, 18, active ? 3 : 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(70));
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1.0f);
        view.setPadding(0, dp(3), 0, dp(3));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, true, GREEN);
        label.setPadding(0, dp(10), 0, 0);
        return label;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setBackground(round(BG, LINE, 14, 1));
        input.setPadding(dp(12), 0, dp(12), 0);
        return input;
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private TrainingData.DayPlan todayPlan() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int index;
        if (day == Calendar.MONDAY) index = 0;
        else if (day == Calendar.TUESDAY) index = 1;
        else if (day == Calendar.WEDNESDAY) index = 2;
        else if (day == Calendar.THURSDAY) index = 3;
        else if (day == Calendar.FRIDAY) index = 4;
        else if (day == Calendar.SATURDAY) index = 5;
        else index = 6;
        return TrainingData.week().get(index);
    }

    private String statusFor(TrainingData.DayPlan plan) {
        if (plan.isWorkout()) return "Силовая тренировка";
        if (plan.title.contains("Волейбол") || plan.title.contains("бокс")) return "День спортивной тренировки";
        return "Отдых";
    }

    private void append(String key, String line) {
        String old = prefs().getString(key, "");
        prefs().edit().putString(key, line + "\n" + old).apply();
    }

    private void appendUniqueWeight(String isoDate, String weight) {
        String old = prefs().getString("weight_entries", "");
        prefs().edit().putString("weight_entries", isoDate + "|" + weight.replace(",", ".") + "\n" + old).apply();
    }

    private boolean hasEntryForToday() {
        String today = isoDate(Calendar.getInstance());
        for (String line : prefs().getString("weight_entries", "").split("\n")) {
            if (line.startsWith(today + "|")) return true;
        }
        return false;
    }

    private void addCompletedDate(String isoDate) {
        Set<String> dates = new HashSet<>();
        String old = prefs().getString("completed_dates", "");
        for (String line : old.split("\n")) if (!line.trim().isEmpty()) dates.add(line.trim());
        dates.add(isoDate);
        StringBuilder builder = new StringBuilder();
        for (String date : dates) builder.append(date).append("\n");
        prefs().edit().putString("completed_dates", builder.toString()).apply();
    }

    private boolean hasCompletedDate(String isoDate) {
        String old = prefs().getString("completed_dates", "");
        for (String line : old.split("\n")) {
            if (isoDate.equals(line.trim())) return true;
        }
        return false;
    }

    private void ensurePlanStartDate() {
        if (prefs().getInt("migration_version", 0) < MIGRATION_VERSION) {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            prefs().edit()
                    .putLong("plan_start_millis", start.getTimeInMillis())
                    .putInt("migration_version", MIGRATION_VERSION)
                    .remove("current_week")
                    .remove("weight_entries")
                    .remove("completed_dates")
                    .remove("workout_log")
                    .remove("exercise_log")
                    .remove("pending_workout_finish")
                    .remove("pending_workout_title")
                    .remove("pending_workout_count")
                    .apply();
            return;
        }
        if (prefs().contains("plan_start_millis")) return;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        prefs().edit()
                .putLong("plan_start_millis", start.getTimeInMillis())
                .putInt("migration_version", MIGRATION_VERSION)
                .apply();
    }

    private long planStartMillis() {
        ensurePlanStartDate();
        return prefs().getLong("plan_start_millis", System.currentTimeMillis());
    }

    private int currentWeek() {
        long diff = System.currentTimeMillis() - planStartMillis();
        int week = 1 + (int) Math.max(0, diff / (7L * DAY_MS));
        return Math.min(12, Math.max(1, week));
    }

    private String value(EditText input) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? "-" : value;
    }

    private String date() {
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Calendar.getInstance().getTime());
    }

    private String isoDate(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private String format(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void requestNotificationPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
    }

    private void openExactAlarmSettings() {
        try {
            startActivity(ReminderScheduler.exactAlarmSettingsIntent(this));
        } catch (Exception ignored) {
            Toast.makeText(this, "Открой разрешения приложения в настройках HyperOS", Toast.LENGTH_LONG).show();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(ReminderScheduler.PREFS, MODE_PRIVATE);
    }

    private String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) + dp(8) : dp(32);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int exerciseImageHeight(String exerciseName, int fallbackDp) {
        if (!usesPortraitExerciseCard(exerciseName)) return dp(fallbackDp);
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(68);
        int portraitHeight = Math.round(availableWidth * 4f / 3f);
        return Math.max(dp(320), Math.min(dp(460), portraitHeight));
    }

    private boolean usesPortraitExerciseCard(String exerciseName) {
        String value = exerciseName.toLowerCase(Locale.ROOT);
        return value.contains("отжимания с рюкзаком")
                || value.contains("ногами на возвыш")
                || value.contains("пауз")
                || value.contains("подтягивания обратным")
                || value.contains("подтягивания прямым")
                || value.contains("сгибания на бицепс")
                || value.contains("молотковые")
                || value.contains("тяга резинки")
                || value.contains("австралийские")
                || value.contains("болгарские")
                || value.contains("приседания с рюкзаком")
                || value.contains("степ")
                || value.contains("reverse crunch")
                || value.contains("подъем ног")
                || value.contains("hollow")
                || value.contains("планк");
    }

    public static class ExerciseAnimationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        private final Bitmap[] bitmaps = new Bitmap[2];
        private int frame;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable animate = new Runnable() {
            @Override
            public void run() {
                frame = (frame + 1) % 2;
                invalidate();
                handler.postDelayed(this, 1500);
            }
        };

        public ExerciseAnimationView(Context context, String name) {
            super(context);
            this.name = name.toLowerCase(Locale.ROOT);
            String slug = slugFor(this.name);
            int first = context.getResources().getIdentifier(slug + "_0", "drawable", context.getPackageName());
            int second = context.getResources().getIdentifier(slug + "_1", "drawable", context.getPackageName());
            bitmaps[0] = BitmapFactory.decodeResource(context.getResources(), first);
            bitmaps[1] = BitmapFactory.decodeResource(context.getResources(), second);
            setBackgroundColor(Color.rgb(247, 248, 244));
        }

        private String slugFor(String value) {
            if (value.contains("отжимания с рюкзаком")) return "pushup_backpack";
            if (value.contains("ногами на возвыш")) return "pushup_elevated";
            if (value.contains("пауз")) return "pushup_pause";
            if (value.contains("узкие")) return "pushup_close";
            if (value.contains("медленные подтягивания")) return "chinup_slow";
            if (value.contains("подтягивания обратным")) return "chinup_reverse";
            if (value.contains("подтягивания прямым")) return "pullup_overhand";
            if (value.contains("молотковые")) return "band_hammer_curl";
            if (value.contains("сгибания на бицепс")) return "band_curl";
            if (value.contains("тяга резинки")) return "band_row";
            if (value.contains("австралийские")) return "australian_pullup";
            if (value.contains("болгарские")) return "bulgarian_split_squat";
            if (value.contains("приседания с рюкзаком")) return "backpack_squat";
            if (value.contains("степ")) return "step_up";
            if (value.contains("румынская")) return "single_leg_rdl";
            if (value.contains("reverse crunch")) return "reverse_crunch";
            if (value.contains("подъем ног")) return "leg_raise";
            if (value.contains("dead bug")) return "dead_bug";
            if (value.contains("hollow")) return "hollow_hold";
            if (value.contains("планк")) return "plank";
            if (value.contains("подтяг")) return "pullup";
            if (value.contains("присед") || value.contains("выпад")) return "squat";
            if (value.contains("сгиб") || value.contains("молот")) return "curl";
            if (value.contains("crunch") || value.contains("ног") || value.contains("dead")) return "core";
            return "pushup";
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            handler.postDelayed(animate, 1500);
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(animate);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(new RectF(18, 18, w - 18, h - 18), 28, 28, fill(Color.rgb(246, 248, 245)));
            Bitmap bitmap = bitmaps[Math.min(frame % 2, 1)];
            if (bitmap != null) {
                RectF dest = fitCenter(bitmap, new RectF(18, 18, w - 18, h - 18));
                canvas.drawBitmap(bitmap, null, dest, paint);
                return;
            }
            drawFloor(canvas, w, h);
            drawPushup(canvas, w, h);
        }

        private RectF fitCenter(Bitmap bitmap, RectF bounds) {
            float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float boundsRatio = bounds.width() / bounds.height();
            float drawWidth;
            float drawHeight;
            if (sourceRatio > boundsRatio) {
                drawWidth = bounds.width();
                drawHeight = drawWidth / sourceRatio;
            } else {
                drawHeight = bounds.height();
                drawWidth = drawHeight * sourceRatio;
            }
            float left = bounds.left + (bounds.width() - drawWidth) / 2f;
            float top = bounds.top + (bounds.height() - drawHeight) / 2f;
            return new RectF(left, top, left + drawWidth, top + drawHeight);
        }

        private Paint fill(int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            return p;
        }

        private void drawFloor(Canvas c, float w, float h) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.rgb(222, 226, 218));
            c.drawLine(36, h - 34, w - 36, h - 34, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void torso(Canvas c, float shoulderX, float shoulderY, float hipX, float hipY, float width, int color) {
            capsule(c, shoulderX, shoulderY, hipX, hipY, width, color);
            float mx = (shoulderX + hipX) / 2f;
            float my = (shoulderY + hipY) / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(Color.argb(95, 255, 255, 255));
            c.drawLine(mx - 4, my - width * .45f, mx + 4, my + width * .45f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void head(Canvas c, float x, float y, float r) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(238, 190, 143));
            c.drawCircle(x, y, r, paint);
            paint.setColor(Color.rgb(142, 99, 54));
            c.drawArc(new RectF(x - r, y - r, x + r, y + r), 195, 160, true, paint);
        }

        private void skin(Canvas c, float x1, float y1, float x2, float y2, float width) {
            capsule(c, x1, y1, x2, y2, width, Color.rgb(246, 202, 158));
        }

        private void shirt(Canvas c, float x1, float y1, float x2, float y2, float width) {
            capsule(c, x1, y1, x2, y2, width, Color.rgb(246, 92, 45));
        }

        private void shorts(Canvas c, float x1, float y1, float x2, float y2, float width) {
            capsule(c, x1, y1, x2, y2, width, Color.rgb(48, 52, 54));
        }

        private void shoe(Canvas c, float x, float y, float angle) {
            c.save();
            c.translate(x, y);
            c.rotate(angle);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(42, 121, 180));
            c.drawRoundRect(new RectF(-14, -5, 16, 7), 7, 7, paint);
            c.restore();
        }

        private void joint(Canvas c, float x, float y) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(246, 202, 158));
            c.drawCircle(x, y, 5, paint);
        }

        private void capsule(Canvas c, float x1, float y1, float x2, float y2, float width, int color) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            c.save();
            c.translate(x1, y1);
            c.rotate(angle);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            c.drawRoundRect(new RectF(0, -width / 2f, length, width / 2f), width / 2f, width / 2f, paint);
            paint.setColor(Color.argb(60, 255, 255, 255));
            c.drawRoundRect(new RectF(length * .12f, -width * .32f, length * .86f, -width * .18f), width / 4f, width / 4f, paint);
            c.restore();
        }

        private void drawPushup(Canvas c, float w, float h) {
            float shoulderY = frame == 1 ? h * .61f : h * .43f;
            float hipY = frame == 1 ? h * .60f : h * .47f;
            float sx = w * .68f, hx = w * .42f, ankleX = w * .22f, handX = w * .78f;
            head(c, sx + 24, shoulderY - 16, 13);
            shirt(c, sx, shoulderY, w * .55f, (shoulderY + hipY) / 2f, 30);
            shorts(c, w * .55f, (shoulderY + hipY) / 2f, hx, hipY, 32);
            skin(c, hx, hipY, ankleX, h - 42, 13);
            skin(c, sx - 2, shoulderY + 5, handX, h - 38, 13);
            skin(c, sx + 13, shoulderY + 8, handX + 22, h - 38, 13);
            joint(c, sx, shoulderY);
            shoe(c, ankleX - 6, h - 42, 0);
        }

        private void drawPullup(Canvas c, float w, float h) {
            float barY = h * .22f;
            paint.setColor(Color.rgb(23, 27, 31));
            paint.setStrokeWidth(8f);
            c.drawLine(w * .24f, barY, w * .76f, barY, paint);
            float shoulderY = frame == 1 ? h * .38f : h * .54f;
            float hipY = shoulderY + 58;
            head(c, w * .50f, shoulderY - 27, 14);
            shirt(c, w * .50f, shoulderY, w * .50f, shoulderY + 36, 34);
            shorts(c, w * .50f, shoulderY + 36, w * .50f, hipY, 33);
            skin(c, w * .47f, shoulderY + 3, w * .35f, barY, 12);
            skin(c, w * .53f, shoulderY + 3, w * .65f, barY, 12);
            skin(c, w * .49f, hipY, w * .43f, h - 38, 13);
            skin(c, w * .51f, hipY, w * .57f, h - 38, 13);
            shoe(c, w * .43f, h - 38, 8);
            shoe(c, w * .57f, h - 38, -8);
        }

        private void drawSquat(Canvas c, float w, float h) {
            float hipY = frame == 1 ? h * .68f : h * .49f;
            float shoulderY = hipY - 70;
            head(c, w * .47f, shoulderY - 25, 14);
            shirt(c, w * .48f, shoulderY, w * .54f, shoulderY + 44, 34);
            shorts(c, w * .54f, shoulderY + 44, w * .55f, hipY, 34);
            skin(c, w * .55f, hipY, w * .42f, frame == 1 ? h - 55 : h - 38, 15);
            skin(c, w * .55f, hipY, w * .70f, h - 38, 15);
            skin(c, w * .49f, shoulderY + 18, w * .68f, shoulderY + 35, 12);
            shoe(c, w * .42f, frame == 1 ? h - 55 : h - 38, 0);
            shoe(c, w * .70f, h - 38, 0);
        }

        private void drawCurl(Canvas c, float w, float h) {
            float sx = w * .46f, sy = h * .36f, hipY = h * .68f;
            head(c, sx, sy - 28, 14);
            shirt(c, sx, sy, sx, sy + 48, 34);
            shorts(c, sx, sy + 48, sx, hipY, 34);
            float handY = frame == 1 ? h * .39f : h * .64f;
            skin(c, sx + 14, sy + 12, w * .65f, handY, 13);
            skin(c, sx - 14, sy + 12, w * .34f, h * .62f, 13);
            skin(c, sx, hipY, w * .38f, h - 38, 14);
            skin(c, sx, hipY, w * .56f, h - 38, 14);
            shoe(c, w * .38f, h - 38, 0);
            shoe(c, w * .56f, h - 38, 0);
        }

        private void drawCore(Canvas c, float w, float h) {
            float torsoY = h * .63f;
            float legY = frame == 1 ? h * .38f : h * .59f;
            head(c, w * .30f, torsoY - 22, 14);
            shirt(c, w * .35f, torsoY, w * .52f, torsoY, 32);
            shorts(c, w * .52f, torsoY, w * .64f, torsoY, 31);
            skin(c, w * .63f, torsoY, w * .82f, legY, 14);
            skin(c, w * .42f, torsoY - 5, w * .28f, frame == 1 ? h * .43f : h - 40, 12);
            shoe(c, w * .82f, legY, -8);
        }
    }

    public static class WeightChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Entry> entries = new ArrayList<>();
        private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        private final SimpleDateFormat label = new SimpleDateFormat("EEE, d MMM", new Locale("ru"));

        public WeightChartView(Context context, String entries) {
            super(context);
            for (String line : entries.split("\n")) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    try {
                        this.entries.add(new Entry(iso.parse(parts[0]).getTime(), Float.parseFloat(parts[1])));
                    } catch (Exception ignored) {
                    }
                }
            }
            Collections.sort(this.entries, Comparator.comparingLong(e -> e.time));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), left = 64, right = 24, top = 26, bottom = 66;
            paint.setStrokeWidth(4);
            paint.setColor(LINE);
            canvas.drawLine(left, h - bottom, w - right, h - bottom, paint);
            canvas.drawLine(left, top, left, h - bottom, paint);
            if (entries.isEmpty()) {
                paint.setColor(MUTED);
                paint.setTextSize(32);
                canvas.drawText("Нет замеров", left + 20, h / 2, paint);
                return;
            }
            float min = entries.get(0).weight, max = entries.get(0).weight;
            long minTime = entries.get(0).time, maxTime = entries.get(entries.size() - 1).time;
            for (Entry entry : entries) {
                min = Math.min(min, entry.weight);
                max = Math.max(max, entry.weight);
            }
            min = (float) Math.floor(min - 0.5f);
            max = (float) Math.ceil(max + 0.5f);
            if (max - min < 1f) max = min + 1f;
            if (maxTime == minTime) maxTime = minTime + DAY_MS;
            paint.setTextSize(22);
            paint.setStrokeWidth(2);
            for (int i = 0; i <= 4; i++) {
                float value = min + (max - min) * i / 4f;
                float y = h - bottom - (h - top - bottom) * i / 4f;
                paint.setColor(LINE);
                canvas.drawLine(left, y, w - right, y, paint);
                paint.setColor(MUTED);
                canvas.drawText(String.format(Locale.US, "%.1f", value), 8, y + 7, paint);
            }
            paint.setColor(GREEN);
            paint.setStrokeWidth(6);
            float prevX = 0, prevY = 0;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                float x = left + (w - left - right) * ((entry.time - minTime) / (float) (maxTime - minTime));
                float y = h - bottom - (h - top - bottom) * ((entry.weight - min) / (max - min));
                if (i > 0) canvas.drawLine(prevX, prevY, x, y, paint);
                canvas.drawCircle(x, y, 8, paint);
                prevX = x;
                prevY = y;
            }
            paint.setTextSize(20);
            paint.setColor(INK);
            int labelStep = Math.max(1, entries.size() / 3);
            for (int i = 0; i < entries.size(); i += labelStep) {
                Entry entry = entries.get(i);
                float x = left + (w - left - right) * ((entry.time - minTime) / (float) (maxTime - minTime));
                String text = label.format(new Date(entry.time));
                canvas.drawText(text, Math.max(6, Math.min(x - 44, w - 112)), h - 22, paint);
            }
            Entry last = entries.get(entries.size() - 1);
            paint.setTextSize(24);
            canvas.drawText(String.format(Locale.US, "%.1f кг", last.weight), left, 22, paint);
        }

        private static class Entry {
            final long time;
            final float weight;

            Entry(long time, float weight) {
                this.time = time;
                this.weight = weight;
            }
        }
    }

    public static class StreakChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Set<String> completed = new HashSet<>();
        private final long startMillis;
        private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        public StreakChartView(Context context, String dates, long startMillis) {
            super(context);
            this.startMillis = startMillis;
            for (String line : dates.split("\n")) if (!line.trim().isEmpty()) completed.add(line.trim());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cellW = (w - 28) / 7f;
            float cellH = (h - 48) / 6f;
            float size = Math.min(cellW - 6, cellH - 6);
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startMillis);
            String[] headers = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
            paint.setTextSize(22);
            paint.setColor(MUTED);
            for (int i = 0; i < 7; i++) canvas.drawText(headers[i], 16 + i * cellW, 24, paint);
            int firstDow = day.get(Calendar.DAY_OF_WEEK);
            int offset = firstDow == Calendar.SUNDAY ? 6 : firstDow - Calendar.MONDAY;
            day.add(Calendar.DAY_OF_YEAR, -offset);
            for (int drawn = 0; drawn < 42; drawn++) {
                int dow = day.get(Calendar.DAY_OF_WEEK);
                String date = iso.format(day.getTime());
                boolean strength = dow == Calendar.MONDAY || dow == Calendar.WEDNESDAY || dow == Calendar.SATURDAY;
                boolean sport = dow == Calendar.TUESDAY || dow == Calendar.THURSDAY;
                int col = drawn % 7;
                int row = drawn / 7;
                float x = 14 + col * cellW;
                float y = 38 + row * cellH;
                int fill = SURFACE;
                int stroke = LINE;
                if (strength) fill = completed.contains(date) ? GREEN : LINE;
                else if (sport) fill = BLUE;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fill);
                canvas.drawRoundRect(new RectF(x, y, x + size, y + size), 12, 12, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(stroke);
                canvas.drawRoundRect(new RectF(x, y, x + size, y + size), 12, 12, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((strength && completed.contains(date)) || sport ? Color.WHITE : MUTED);
                paint.setTextSize(Math.max(18, size * .34f));
                canvas.drawText(String.valueOf(day.get(Calendar.DAY_OF_MONTH)), x + size * .20f, y + size * .58f, paint);
                day.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
    }
}
