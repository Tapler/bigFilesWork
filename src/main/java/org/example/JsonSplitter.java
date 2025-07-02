package org.example;

import com.fasterxml.jackson.core.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Потоково делит большой JSON-файл с paymentRegistryItem на части по maxItems.
 * Шапка (корневые поля) сохраняется, массив пишется по частям.
 * Запуск через меню: выбор файла и лимита.
 */
public class JsonSplitter {
    public static void main(String[] args) throws Exception {
        // Диалог выбора файла и лимита
        EventQueue.invokeAndWait(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите JSON-файл для разбиения");
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                String inputPath = chooser.getSelectedFile().getAbsolutePath();
                String maxStr = JOptionPane.showInputDialog(null, "Максимальное количество записей в части:", "10000");
                if (maxStr == null) return;
                int maxItems;
                try {
                    maxItems = Integer.parseInt(maxStr.trim());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Некорректное число");
                    return;
                }
                try {
                    splitJsonStreaming(inputPath, maxItems);
                    JOptionPane.showMessageDialog(null, "Готово!");
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Потоково делит JSON на части, не загружая весь массив в память
     */
    private static void splitJsonStreaming(String inputPath, int maxItems) throws Exception {
        JsonFactory factory = new JsonFactory();
        File inputFile = new File(inputPath);
        String baseName = inputFile.getName().replaceFirst("\\.json$", "");
        // Каталог для частей: <имя_файла>_splitted
        File outDir = new File(inputFile.getParentFile(), baseName + "_splitted");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Не удалось создать директорию " + outDir.getAbsolutePath());
        }
        try (JsonParser parser = factory.createParser(inputFile)) {
            // 1. Читаем корневой объект и сохраняем шапку (все поля кроме paymentRegistryItem)
            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IOException("Ожидался JSON-объект");

            // Буфер для корневых полей
            ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
            JsonGenerator headerGen = factory.createGenerator(headerOut, JsonEncoding.UTF8);
            headerGen.writeStartObject();

            // Найдём массив и запомним поля шапки
            boolean foundArray = false;
            while (parser.nextToken() != null) {
                String fieldName = parser.getCurrentName();
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    parser.nextToken();
                    if ("paymentRegistryItem".equals(fieldName)) {
                        foundArray = true;
                        break; // дошли до массива, дальше — элементы
                    } else {
                        // Копируем поле шапки
                        headerGen.writeFieldName(fieldName);
                        headerGen.copyCurrentStructure(parser);
                    }
                }
            }
            headerGen.writeEndObject();
            headerGen.close();
            if (!foundArray)
                throw new IOException("Не найден массив paymentRegistryItem");

            // 2. Чтение массива и запись чанков
            int fileIdx = 1;
            int itemCount = 0;
            JsonGenerator outGen = null;
            OutputStream outStream = null;
            boolean insideObject = false; // Флаг: открыт ли корневой объект
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (itemCount % maxItems == 0) {
                    // Закрыть предыдущий файл, если был
                    if (outGen != null) {
                        if (insideObject) {
                            outGen.writeEndArray();
                            outGen.writeEndObject();
                            insideObject = false;
                        }
                        outGen.close();
                        outStream.close();
                    }
                    // Открыть новый файл и записать шапку
                    String outPath = new File(outDir, String.format("%s_part_%d.json", baseName, fileIdx++)).getAbsolutePath();
                    outStream = new FileOutputStream(outPath);
                    outGen = factory.createGenerator(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));
                    outGen.useDefaultPrettyPrinter();
                    // Вставляем шапку вручную, затем paymentRegistryItem
                    try (JsonParser hParser = factory.createParser(headerOut.toByteArray())) {
                        if (hParser.nextToken() != JsonToken.START_OBJECT)
                            throw new IOException("Ожидался объект шапки");
                        outGen.writeStartObject();
                        while (hParser.nextToken() != JsonToken.END_OBJECT) {
                            String hField = hParser.getCurrentName();
                            hParser.nextToken();
                            outGen.writeFieldName(hField);
                            outGen.copyCurrentStructure(hParser);
                        }
                        outGen.writeFieldName("paymentRegistryItem");
                        outGen.writeStartArray();
                        insideObject = true;
                    }
                }
                // Копируем очередной элемент массива
                outGen.copyCurrentStructure(parser);
                itemCount++;
            }
            // Закрыть последний файл
            if (outGen != null && insideObject) {
                outGen.writeEndArray();
                outGen.writeEndObject();
                outGen.close();
                outStream.close();
            }
        }
    }
}
