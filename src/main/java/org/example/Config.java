package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (input != null) {
                // Простейший YAML reader: ищем строки вида key: value
                java.util.Scanner scanner = new java.util.Scanner(input, "UTF-8");
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.startsWith("#") && line.contains(":")) {
                        String[] parts = line.split(":", 2);
                        String key = parts[0].trim();
                        String value = parts[1].trim().replaceAll("^\"|\"$", "");
                        properties.setProperty(key, value);
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Не удалось загрузить application.yml", ex);
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }
}
