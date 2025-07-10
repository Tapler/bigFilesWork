package org.example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

public class XmlToJsonJacksonStaxConverter {
    public static void main(String[] args) throws Exception {
        // Замер памяти и времени до парсинга
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        // Пути к исходному XML и результирующему JSON
        String xmlPath = "output_big_7057000_1.xml";
        // Формируем путь к JSON-файлу на основе пути к XML-файлу, заменяя расширение на .json
        String jsonPath = xmlPath.replaceAll("\\.xml$", ".json");

        // --- Новый формат: собираем значения для итоговой структуры ---
        String registryUID = null;
        String registryDate = null;
        String registrySum = null;
        String senderId = "1";
        String registryNumber = null;

        // Безопасная настройка XMLInputFactory для предотвращения XXE-атак
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Отключаем внешние сущности и DTD
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false); // запретить DTD
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false); // запретить внешние сущности
        XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(xmlPath));

        // --- Запись в файл JSON потоково ---
        try (JsonGenerator gen = new JsonFactory().createGenerator(
                new OutputStreamWriter(new FileOutputStream(jsonPath), StandardCharsets.UTF_8))) {
            gen.useDefaultPrettyPrinter();
            gen.writeStartObject();

            // --- Формируем registry ---
            gen.writeObjectFieldStart("registry");

            // --- Формируем items ---
            gen.writeObjectFieldStart("items");
            // payer
            gen.writeObjectFieldStart("payer");
            gen.writeStringField("accountNumber", "12345678901234567890");
            gen.writeNullField("bic");
            gen.writeNullField("inn");
            gen.writeNumberField("currencyId", 2);
            gen.writeNullField("currencyCode");
            gen.writeEndObject(); // payer

            // --- Массив записей (records) ---
            gen.writeArrayFieldStart("records");

            int paymentCount = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (name.equals("PaymentsRegistrySegment")) {
                        registryUID = reader.getAttributeValue(null, "RegistryUID");
                        registryDate = formatDate(reader.getAttributeValue(null, "RegistryDate"));
                        registrySum = reader.getAttributeValue(null, "RegistrySum");
                        registryNumber = reader.getAttributeValue(null, "SegmentNumber");
                    } else if (name.equals("Payment")) {
                        paymentCount++;
                        // --- Потоковая запись одной записи ---
                        gen.writeStartObject();
                        gen.writeStringField("number", Integer.toString(paymentCount));
                        gen.writeStringField("externalId", reader.getAttributeValue(null, "RegistryStringUID"));
                        gen.writeStringField("date", registryDate);
                        // details.registerPayments
                        gen.writeObjectFieldStart("details");
                        gen.writeObjectFieldStart("registerPayments");
                        gen.writeNumberField("amount", parseAmount(reader.getAttributeValue(null, "PaymentSum")));
                        // payee
                        gen.writeObjectFieldStart("payee");
                        gen.writeStringField("brief", reader.getAttributeValue(null, "ReceiverName"));
                        gen.writeStringField("accountNumber", reader.getAttributeValue(null, "ReceiverAccountNumber"));
                        gen.writeStringField("bic", reader.getAttributeValue(null, "ReceiverBankBic"));
                        gen.writeStringField("inn", reader.getAttributeValue(null, "ReceiverINN"));
                        gen.writeNumberField("currencyId", 2);
                        gen.writeNullField("currencyCode");
                        gen.writeEndObject(); // payee
                        gen.writeStringField("purpose", reader.getAttributeValue(null, "PaymentPurpose"));
                        gen.writeNumberField("currencyId", 2);
                        gen.writeNullField("currencyCode");
                        gen.writeStringField("paymentType", reader.getAttributeValue(null, "PaymentType"));
                        gen.writeStringField("operationKind", reader.getAttributeValue(null, "OperationKind"));
                        gen.writeEndObject(); // registerPayments
                        gen.writeEndObject(); // details
                        gen.writeEndObject(); // record
                    }
                }
            }
            gen.writeEndArray(); // records
            gen.writeEndObject(); // items

            reader.close();

            // --- Теперь заполняем registry (после цикла, когда известны все значения) ---
            gen.writeFieldName("registry");
            gen.writeStartObject();
            gen.writeStringField("number", registryNumber);
            gen.writeNumberField("numberItems", paymentCount);
            gen.writeStringField("date", registryDate);
            gen.writeStringField("kind", "registerPayments");
            // type как объект
            gen.writeObjectFieldStart("type");
            gen.writeNumberField("id", 6);
            gen.writeStringField("brief", "Тип реестра 6");
            gen.writeEndObject();
            // sender
            gen.writeObjectFieldStart("sender");
            gen.writeNumberField("id", Integer.valueOf(senderId));
            gen.writeEndObject();
            // recipient
            gen.writeObjectFieldStart("recipient");
            gen.writeNullField("id");
            gen.writeNullField("brief");
            gen.writeEndObject();
            // department
            gen.writeObjectFieldStart("department");
            gen.writeNumberField("id", 1000);
            gen.writeNullField("brief");
            gen.writeEndObject();
            gen.writeStringField("externalId", registryUID);
            // metadata.registerPayments
            gen.writeObjectFieldStart("metadata");
            gen.writeObjectFieldStart("registerPayments");
            gen.writeNumberField("amount", parseAmount(registrySum));
            gen.writeNumberField("currencyId", 2);
            gen.writeNullField("currencyCode");
            gen.writeEndObject(); // registerPayments
            gen.writeEndObject(); // metadata
            gen.writeEndObject(); // registry

            gen.writeEndObject(); // root
        }

//        // --- Сохраняем base64 версию рядом с json ---
//        // Оптимизация: потоковое кодирование Base64, не читаем весь файл в память
//        String base64Path = jsonPath + ".base64";
//        try (
//            java.io.InputStream jsonIn = new java.io.FileInputStream(jsonPath);
//            java.io.OutputStream base64Out = new java.io.FileOutputStream(base64Path);
//            java.io.OutputStream b64Stream = java.util.Base64.getEncoder().wrap(base64Out)
//        ) {
//            byte[] buffer = new byte[8192];
//            int len;
//            while ((len = jsonIn.read(buffer)) != -1) {
//                b64Stream.write(buffer, 0, len);
//            }
//        } catch (Exception e) {
//            System.err.println("Ошибка при сохранении base64-файла: " + e.getMessage());
//        }

        // Замер памяти и времени после парсинга
        long endTime = System.currentTimeMillis();
        long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Использовано памяти: %.2f MB\n", (usedMemoryAfter - usedMemoryBefore) / 1024.0 / 1024.0);
        System.out.printf("Время выполнения: %.2f сек\n", (endTime - startTime) / 1000.0);
        System.out.println("JSON успешно создан (новый формат): " + jsonPath);
    }

    // Преобразует дату к формату yyyy-MM-dd
    private static String formatDate(String input) {
        if (input == null || input.isEmpty()) return null;
        // Если уже в формате yyyy-MM-dd
        if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        // Если ISO или содержит T
        int tIdx = input.indexOf('T');
        if (tIdx > 0) return input.substring(0, tIdx);
        // Если есть пробел после даты
        int spaceIdx = input.indexOf(' ');
        if (spaceIdx > 0) return input.substring(0, spaceIdx);
        // В остальных случаях — попробуем взять только первые 10 символов
        if (input.length() >= 10) return input.substring(0, 10);
        return input;
    }

    // Парсинг суммы (String -> BigDecimal), если null или пусто — вернуть 0
    private static BigDecimal parseAmount(String sum) {
        if (sum == null || sum.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(sum.replace(",", "."));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
