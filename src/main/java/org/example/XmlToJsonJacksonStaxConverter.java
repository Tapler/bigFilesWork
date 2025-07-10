package org.example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlToJsonJacksonStaxConverter {
    public static void main(String[] args) throws Exception {
        // Замер памяти и времени до парсинга
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        // Пути к исходному XML и результирующему JSON
        String xmlPath = "output_big_1557000_1.xml";
        // Формируем путь к JSON-файлу на основе пути к XML-файлу, заменяя расширение на .json
        String jsonPath = xmlPath.replaceAll("\\.xml$", ".json");

        // --- Новый формат: собираем значения для итоговой структуры ---
        String registryUID = null;
        String registryDate = null;
        String deliveryOrganization = null;
        String registrySum = null;
        String registryType = null;
        // Для примера жёстко задаём sender, recipient, department (можно доработать под ваши данные)
        String senderId = "1";
        String senderBrief = "Пенсионный фонд России";
        String registryNumber = null; // в XML нет, можно задать registryUID или null
        int numberItems = 0;

        List<Map<String, Object>> records = new ArrayList<>();

        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(xmlPath));

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if (name.equals("PaymentsRegistrySegment")) {
                    registryUID = reader.getAttributeValue(null, "RegistryUID");
                    registryDate = formatDate(reader.getAttributeValue(null, "RegistryDate")); // преобразуем к yyyy-MM-dd
                    deliveryOrganization = reader.getAttributeValue(null, "DeliveryOrganization");
                    registrySum = reader.getAttributeValue(null, "RegistrySum");
                    registryType = reader.getAttributeValue(null, "RegistryType");
                    // registryNumber = ... // если нужно, можно взять RegistryUID или отдельный атрибут
                } else if (name.equals("Payment")) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("number", Integer.toString(records.size() + 1));
                    record.put("externalId", reader.getAttributeValue(null, "RegistryStringUID"));
                    record.put("date", registryDate);
                    // details.registerPayments
                    Map<String, Object> details = new HashMap<>();
                    Map<String, Object> regPay = new HashMap<>();
                    regPay.put("amount", parseAmount(reader.getAttributeValue(null, "PaymentSum")));
                    // payee
                    Map<String, Object> payee = new HashMap<>();
                    payee.put("brief", reader.getAttributeValue(null, "ReceiverName"));
                    payee.put("accountNumber", reader.getAttributeValue(null, "ReceiverAccountNumber"));
                    payee.put("bic", reader.getAttributeValue(null, "ReceiverBankBic"));
                    payee.put("inn", reader.getAttributeValue(null, "ReceiverINN"));
                    payee.put("currencyId", 2);
                    payee.put("currencyCode", null);
                    regPay.put("payee", payee);
                    regPay.put("purpose", reader.getAttributeValue(null, "PaymentPurpose"));
                    regPay.put("currencyId", 2);
                    regPay.put("currencyCode", null);
                    regPay.put("paymentType", reader.getAttributeValue(null, "PaymentType"));
                    regPay.put("operationKind", reader.getAttributeValue(null, "OperationKind"));
                    details.put("registerPayments", regPay);
                    record.put("details", details);
                    records.add(record);
                }
            }
        }
        reader.close();
        numberItems = records.size();

        // --- Формируем итоговую структуру ---
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> registry = new HashMap<>();
        registry.put("number", registryNumber != null ? registryNumber : registryUID);
        registry.put("numberItems", numberItems);
        registry.put("date", registryDate);
        registry.put("kind", "registerPayments"); // новое поле kind по схеме
        // type как объект
        Map<String, Object> typeObj = new HashMap<>();
        typeObj.put("id", 6);
        typeObj.put("brief", "Тип реестра 6");
        registry.put("type", typeObj);
        Map<String, Object> sender = new HashMap<>();
        sender.put("id", Integer.valueOf(senderId));
        sender.put("brief", senderBrief);
        registry.put("sender", sender);
        Map<String, Object> recipient = new HashMap<>();
        recipient.put("id", null);
        recipient.put("brief", null);
        registry.put("recipient", recipient);
        Map<String, Object> department = new HashMap<>();
        department.put("id", 1000); // теперь всегда 1000
        department.put("brief", null);
        registry.put("department", department);
        registry.put("externalId", registryUID);
        // metadata.registerPayments
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> regPayMeta = new HashMap<>();
        regPayMeta.put("amount", parseAmount(registrySum));
        regPayMeta.put("currencyId", 2);
        regPayMeta.put("currencyCode", null);
        metadata.put("registerPayments", regPayMeta);
        registry.put("metadata", metadata);
        root.put("registry", registry);

        // items
        Map<String, Object> items = new HashMap<>();
        Map<String, Object> payer = new HashMap<>();
        payer.put("brief", "ПФР");
        payer.put("accountNumber", "12345678901234567890");
        payer.put("bic", null);
        payer.put("inn", null);
        payer.put("currencyId", 2);
        payer.put("currencyCode", null);
        items.put("payer", payer);
        items.put("records", records);
        root.put("items", items);

        // --- Запись в файл ---
        try (JsonGenerator gen = new JsonFactory().createGenerator(
                new OutputStreamWriter(new FileOutputStream(jsonPath), StandardCharsets.UTF_8))) {
            gen.useDefaultPrettyPrinter();
            new ObjectMapper().writeValue(gen, root);
        }

        // --- Сохраняем base64 версию рядом с json ---
        String base64Path = jsonPath + ".base64";
        try {
            byte[] jsonBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(jsonPath));
            String base64 = java.util.Base64.getEncoder().encodeToString(jsonBytes);
            java.nio.file.Files.write(java.nio.file.Paths.get(base64Path), base64.getBytes(StandardCharsets.UTF_8));
            System.out.println("Base64-файл успешно сохранён: " + base64Path);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении base64-файла: " + e.getMessage());
        }

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
