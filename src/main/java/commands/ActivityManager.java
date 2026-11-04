package commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import database.Database;
import utils.Log;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ActivityManager extends ListenerAdapter {


    public static class Spec {
        public String type;
        public String text;
        public String url;

        public Spec() {}
        public Spec(String type, String text, String url) {
            this.type = type; this.text = text; this.url = url;
        }
    }

    public static class HistoryEntry {
        public Spec spec;
        public String setAt;
        public String setBy;
    }

    public static class Rotation {
        public boolean enabled = false;
        public int intervalSeconds = 60;
        public String mode = "sequential";
        public List<String> presets = new ArrayList<>();
    }

    public static class ScheduleEntry {
        public String time;
        public String zone = "UTC";
        public String preset;
    }

    public static class EventRule {
        public String event;
        public Integer value;
        public String preset;
    }

    public static class Data {
        public Spec current;
        public Rotation rotation = new Rotation();
        public Map<String, Spec> presets = new LinkedHashMap<>();
        public List<ScheduleEntry> schedule = new ArrayList<>();
        public List<EventRule> events = new ArrayList<>();
        public List<HistoryEntry> history = new ArrayList<>();
    }


    private static final ActivityManager INSTANCE = new ActivityManager();
    public static ActivityManager get() { return INSTANCE; }

    private static final int MAX_HISTORY = 50;
    private static final int MAX_TEXT = 128;

    private final Path file = resolveFile();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, String> lastFired = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private Data data = new Data();
    private JDA jda;
    private ScheduledFuture<?> rotationTask;
    private int rotationIndex = 0;
    private boolean initialized;
    private boolean shutdown;

    private ActivityManager() {}

    private static Path resolveFile() {
        Path file = Database.dataFile("activity.json");
        Path legacy = Path.of("data", "activity.json");
        if (!Files.exists(file) && Files.exists(legacy)) {
            return legacy;
        }
        return file;
    }


    public synchronized void init(JDA jda) {
        if (shutdown) {
            throw new IllegalStateException("ActivityManager cannot be restarted after shutdown");
        }
        if (initialized) {
            return;
        }
        this.jda = jda;
        load();
        if (data.rotation.enabled) {
            startRotation();
        } else if (data.current != null) {
            applyInternal(data.current, null);
        }
        scheduler.scheduleAtFixedRate(this::checkSchedule, 10, 30, TimeUnit.SECONDS);
        initialized = true;
    }

    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        if (rotationTask != null) {
            rotationTask.cancel(false);
            rotationTask = null;
        }
        scheduler.shutdownNow();
    }

    @Override
    public synchronized void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (initialized) {
            onEvent("MEMBER_MILESTONE", event.getGuild().getMemberCount(), event.getGuild());
        }
    }


    public synchronized String set(Spec spec, Guild context, String userId) {
        String error = validate(spec);
        if (error != null) return error;
        stopRotation();
        applyInternal(spec, context);
        data.current = spec;
        addHistory(spec, userId);
        save();
        return null;
    }

    public synchronized void reset() {
        stopRotation();
        data.current = null;
        if (jda != null) {
            jda.getPresence().setActivity(null);
        }
        save();
    }


    public synchronized String savePreset(String name, Spec spec) {
        String error = validate(spec);
        if (error != null) return error;
        data.presets.put(name.toLowerCase(), spec);
        save();
        return null;
    }

    public synchronized boolean deletePreset(String name) {
        boolean removed = data.presets.remove(name.toLowerCase()) != null;
        if (removed) {
            data.rotation.presets.remove(name.toLowerCase());
            save();
        }
        return removed;
    }

    public synchronized Map<String, Spec> listPresets() {
        return new LinkedHashMap<>(data.presets);
    }

    public synchronized String usePreset(String name, Guild context, String userId) {
        Spec spec = data.presets.get(name.toLowerCase());
        if (spec == null) return "No preset named `" + name + "`.";
        return set(spec, context, userId);
    }

    public synchronized String random(Guild context, String userId) {
        if (data.presets.isEmpty()) return "No saved presets to choose from.";
        List<Spec> all = new ArrayList<>(data.presets.values());
        return set(all.get(random.nextInt(all.size())), context, userId);
    }

    public synchronized List<HistoryEntry> history(int limit) {
        int from = Math.max(0, data.history.size() - limit);
        return new ArrayList<>(data.history.subList(from, data.history.size()));
    }


    public synchronized String configureRotation(List<String> presetNames, int seconds, String mode) {
        if (seconds < 30) return "Interval must be at least 30 seconds (Discord rate limits).";
        for (String n : presetNames) {
            if (!data.presets.containsKey(n.toLowerCase())) return "Unknown preset: `" + n + "`.";
        }
        data.rotation.presets = presetNames.stream().map(String::toLowerCase).toList();
        data.rotation.intervalSeconds = seconds;
        data.rotation.mode = mode;
        data.rotation.enabled = true;
        save();
        startRotation();
        return null;
    }

    public synchronized void stopRotationAndSave() {
        stopRotation();
        save();
    }

    private void startRotation() {
        stopRotation();
        if (data.rotation.presets.isEmpty()) return;
        data.rotation.enabled = true;
        rotationTask = scheduler.scheduleAtFixedRate(this::rotateOnce,
                0, data.rotation.intervalSeconds, TimeUnit.SECONDS);
    }

    private void stopRotation() {
        if (rotationTask != null) rotationTask.cancel(false);
        rotationTask = null;
        data.rotation.enabled = false;
    }

    private synchronized void rotateOnce() {
        List<String> names = data.rotation.presets;
        if (names.isEmpty()) return;
        String name = "random".equals(data.rotation.mode)
                ? names.get(random.nextInt(names.size()))
                : names.get(rotationIndex++ % names.size());
        Spec spec = data.presets.get(name);
        if (spec != null) applyInternal(spec, null);
    }


    public synchronized String addSchedule(String time, String zone, String preset) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            ZoneId.of(zone);
        } catch (Exception e) {
            return "Use a 24h time like `22:00` and a valid zone like `Asia/Dhaka`.";
        }
        if (!data.presets.containsKey(preset.toLowerCase())) return "Unknown preset.";
        ScheduleEntry entry = new ScheduleEntry();
        entry.time = time; entry.zone = zone; entry.preset = preset.toLowerCase();
        data.schedule.add(entry);
        save();
        return null;
    }

    private synchronized void checkSchedule() {
        for (ScheduleEntry e : data.schedule) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(e.zone));
            String nowStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));
            String key = e.zone + e.time + e.preset;
            String stamp = now.toLocalDate() + nowStr;
            if (nowStr.equals(e.time) && !stamp.equals(lastFired.get(key))) {
                lastFired.put(key, stamp);
                Spec spec = data.presets.get(e.preset);
                if (spec != null) {
                    stopRotation();
                    applyInternal(spec, null);
                    data.current = spec;
                    save();
                }
            }
        }
    }


    public synchronized String addEventRule(String event, Integer value, String preset) {
        if (!data.presets.containsKey(preset.toLowerCase())) return "Unknown preset.";
        EventRule rule = new EventRule();
        rule.event = event.toUpperCase(); rule.value = value; rule.preset = preset.toLowerCase();
        data.events.add(rule);
        save();
        return null;
    }

    public synchronized void onEvent(String event, Integer value, Guild guild) {
        for (EventRule r : data.events) {
            if (!r.event.equalsIgnoreCase(event)) continue;
            if (r.value != null && !r.value.equals(value)) continue;
            Spec spec = data.presets.get(r.preset);
            if (spec == null) continue;
            stopRotation();
            applyInternal(spec, guild);
            data.current = spec;
            save();
            return;
        }
    }


    private void applyInternal(Spec spec, Guild context) {
        String text = resolvePlaceholders(spec.text, context);
        Activity activity = switch (spec.type) {
            case "playing"   -> Activity.playing(text);
            case "watching"  -> Activity.watching(text);
            case "listening" -> Activity.listening(text);
            case "competing" -> Activity.competing(text);
            case "streaming" -> Activity.streaming(text, spec.url);
            default          -> Activity.customStatus(text);
        };
        jda.getPresence().setActivity(activity);
    }

    private String resolvePlaceholders(String s, Guild g) {
        int members = g != null ? g.getMemberCount()
                : jda.getGuilds().stream().mapToInt(Guild::getMemberCount).sum();
        return s.replace("{member_count}", String.valueOf(members))
                .replace("{server_count}", String.valueOf(jda.getGuilds().size()));
    }

    private String validate(Spec spec) {
        Set<String> types = Set.of("playing", "watching", "listening", "competing", "custom", "streaming");
        if (spec.type == null || !types.contains(spec.type)) return "Invalid activity type.";
        if (spec.text == null || spec.text.isBlank()) return "Activity text can't be empty.";
        if (spec.text.length() > MAX_TEXT) return "Activity text is too long (max " + MAX_TEXT + ").";
        if ("streaming".equals(spec.type)
                && (spec.url == null || !Activity.isValidStreamingUrl(spec.url))) {
            return "Streaming needs a valid Twitch or YouTube URL.";
        }
        return null;
    }

    private void addHistory(Spec spec, String userId) {
        HistoryEntry h = new HistoryEntry();
        h.spec = spec;
        h.setAt = Instant.now().toString();
        h.setBy = userId;
        data.history.add(h);
        while (data.history.size() > MAX_HISTORY) data.history.remove(0);
    }


    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Data loaded = gson.fromJson(Files.readString(file), Data.class);
            if (loaded != null) {
                if (loaded.rotation == null) loaded.rotation = new Rotation();
                if (loaded.rotation.presets == null) loaded.rotation.presets = new ArrayList<>();
                if (loaded.presets == null) loaded.presets = new LinkedHashMap<>();
                if (loaded.schedule == null) loaded.schedule = new ArrayList<>();
                if (loaded.events == null) loaded.events = new ArrayList<>();
                if (loaded.history == null) loaded.history = new ArrayList<>();
                data = loaded;
            }
        } catch (Exception e) {
            Path backup = file.resolveSibling("activity.json.broken-" + System.currentTimeMillis());
            try {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                Log.error("Couldn't read " + file + "; the activity settings start empty. "
                        + "The unreadable file was kept as " + backup.getFileName() + ".", e);
            } catch (IOException copyError) {
                Log.error("Couldn't read " + file + " and couldn't back it up either.", e);
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("activity.json.tmp");
            Files.writeString(tmp, gson.toJson(data));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.error("Couldn't save the activity settings to " + file, e);
        }
    }
}