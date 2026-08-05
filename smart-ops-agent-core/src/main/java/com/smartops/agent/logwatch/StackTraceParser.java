package com.smartops.agent.logwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java堆栈跟踪解析器（阶段七代码定位增强）。
 */
public class StackTraceParser {

    private static final Pattern FRAME_PATTERN =
            Pattern.compile("at\\s+([\\w.$]+)\\(([\\w.$ ]+):?(\\d*)\\)");
    private static final Pattern NATIVE_PATTERN =
            Pattern.compile("at\\s+([\\w.$]+)\\(Native Method\\)");

    public record CodeLocation(String className, String fileName, int lineNumber) {}

    public List<CodeLocation> parse(String stackTrace) {
        List<CodeLocation> locations = new ArrayList<>();
        if (stackTrace == null || stackTrace.isBlank()) return locations;
        Matcher m = FRAME_PATTERN.matcher(stackTrace);
        while (m.find() && locations.size() < 10) {
            String file = m.group(2).trim();
            String lineStr = m.group(3);
            int line = lineStr.isEmpty() ? 0 : Integer.parseInt(lineStr);
            locations.add(new CodeLocation(m.group(1), file, line));
        }
        Matcher nm = NATIVE_PATTERN.matcher(stackTrace);
        while (nm.find() && locations.size() < 10) {
            locations.add(new CodeLocation(nm.group(1), "Native Method", 0));
        }
        return locations;
    }

    public String formatForPrompt(List<CodeLocation> locations, String repoUrl) {
        if (locations.isEmpty() || repoUrl == null || repoUrl.isBlank()) return "";
        StringBuilder sb = new StringBuilder("\n【代码定位】\n");
        for (int i = 0; i < Math.min(locations.size(), 5); i++) {
            CodeLocation loc = locations.get(i);
            sb.append(i + 1).append(". ").append(loc.className)
                    .append(" (").append(loc.fileName);
            if (loc.lineNumber > 0) sb.append(":").append(loc.lineNumber);
            sb.append(")\n");
        }
        return sb.toString();
    }
}
