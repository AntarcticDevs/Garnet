package config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;






final class ConfigUpgrader {
    private static final Pattern TOP_KEY = Pattern.compile("^([A-Za-z0-9_-]+):.*$");
    private static final Pattern MODULE_LINE = Pattern.compile("^ {2}([A-Za-z0-9_-]+):.*$");
    private static final Set<String> NEVER_ADDED = Set.of("token");

    private ConfigUpgrader() {
    }


    record Result(String text, List<String> added, List<String> notes) {
    }


    static List<String> run() throws IOException {
        Path path = ConfigManager.configPath();
        String original = Files.readString(path);
        Set<String> modulesPresent = ConfigManager.keysUnder("modules");
        Set<String> topPresent = new LinkedHashSet<>();
        for (String key : topLevelKeys(ConfigManager.DEFAULT_CONFIG)) {
            if (ConfigManager.hasKey(key)) {
                topPresent.add(key);
            }
        }
        Result result = upgradeText(original, ConfigManager.DEFAULT_CONFIG, topPresent, modulesPresent,
                ConfigManager.isSection("modules"));
        for (String note : result.notes()) {
            utils.Log.warn(note);
        }
        List<String> report = new ArrayList<>(result.added());
        if (result.added().isEmpty()) {
            return report;
        }
        try {
            new Yaml().load(new java.io.StringReader(result.text()));
        } catch (YAMLException exception) {
            report.clear();
            report.add("Couldn't add new settings automatically (config.yml was left as it is).");
            return report;
        }
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, result.text());
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        ConfigManager.load();
        return report;
    }


    static Result upgradeText(String original, String template, Set<String> topPresent, Set<String> modulesPresent,
            boolean modulesIsSection) {
        String eol = original.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(original.split("\r?\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        List<String> added = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> templateLines = List.of(template.split("\n", -1));


        int modulesIndex = indexOfKey(lines, "modules");
        List<String> missingModules = new ArrayList<>();
        for (String line : templateLines) {
            Matcher matcher = MODULE_LINE.matcher(line);
            if (matcher.matches() && !modulesPresent.contains(matcher.group(1))) {
                missingModules.add(line);
                added.add("modules." + matcher.group(1));
            }
        }
        if (modulesIndex >= 0 && !missingModules.isEmpty()) {
            if (!modulesIsSection || lines.get(modulesIndex).matches("^modules:\\s*\\S.*$")
                    && !lines.get(modulesIndex).matches("^modules:\\s*#.*$")) {
                notes.add("modules in config.yml is written in a form I can't extend; add the new features by "
                        + "hand (missing ones stay on).");
                added.removeIf(name -> name.startsWith("modules."));
            } else {
                int last = modulesIndex;
                for (int index = modulesIndex + 1; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (line.matches("^\\s+\\S.*$")) {
                        last = index;
                    } else if (!line.isBlank()) {
                        break;
                    }
                }
                lines.addAll(last + 1, missingModules);
            }
        }


        List<String> append = new ArrayList<>();
        boolean modulesBlockNeeded = modulesIndex < 0;
        for (int index = 0; index < templateLines.size(); index++) {
            Matcher key = TOP_KEY.matcher(templateLines.get(index));
            if (!key.matches()) {
                continue;
            }
            String name = key.group(1);
            if (NEVER_ADDED.contains(name) || topPresent.contains(name) || indexOfKey(lines, name) >= 0) {
                continue;
            }
            int start = index;
            while (start > 0 && templateLines.get(start - 1).startsWith("#")) {
                start--;
            }
            if (!append.isEmpty() && start < index) {
                append.add("");
            }
            if (name.equals("modules")) {
                if (!modulesBlockNeeded) {
                    continue;
                }
                int end = index + 1;
                while (end < templateLines.size() && MODULE_LINE.matcher(templateLines.get(end)).matches()) {
                    end++;
                }
                append.addAll(templateLines.subList(start, end));
                added.removeIf(item -> item.startsWith("modules."));
                added.add("modules");
            } else {
                append.addAll(templateLines.subList(start, index + 1));
                added.add(name);
            }
        }
        if (!append.isEmpty()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.addAll(append);
        }
        return new Result(String.join(eol, lines) + eol, added, notes);
    }

    private static int indexOfKey(List<String> lines, String key) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(key) + ":.*$");
        for (int index = 0; index < lines.size(); index++) {
            if (pattern.matcher(lines.get(index)).matches()) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> topLevelKeys(String template) {
        List<String> keys = new ArrayList<>();
        for (String line : template.split("\n")) {
            Matcher matcher = TOP_KEY.matcher(line);
            if (matcher.matches()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }
}
