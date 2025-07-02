package org.example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class XmlToJsonJacksonStaxConverter {
    public static void main(String[] args) throws Exception {
        // Замер памяти и времени до парсинга
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        // Пути к исходному XML и результирующему JSON
        String xmlPath = "output_big_2000000_1.xml";
        String jsonPath = "output_big_2000000_1.json";



        // Для замера пика памяти
        long peakMemory = usedMemoryBefore;

        // Создаём потоковый XML-парсер (StAX)
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(xmlPath));

        // Создаём потоковый JSON-генератор (Jackson Streaming API)
        try (JsonGenerator gen = new JsonFactory().createGenerator(
                new OutputStreamWriter(new FileOutputStream(jsonPath), StandardCharsets.UTF_8))) {
            gen.useDefaultPrettyPrinter(); // Красивое форматирование JSON
            gen.writeStartObject(); // Начало корневого объекта JSON
            boolean arrayStarted = false;
            int paymentCount = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if (name.equals("PaymentsRegistrySegment")) {
                        // Корневые атрибуты XML -> поля JSON
                        gen.writeStringField("RegistryUID", reader.getAttributeValue(null, "RegistryUID"));
                        gen.writeStringField("RegistryDate", reader.getAttributeValue(null, "RegistryDate"));
                        gen.writeStringField("RegistrySum", reader.getAttributeValue(null, "RegistrySum"));
                        gen.writeStringField("RegistryType", reader.getAttributeValue(null, "RegistryType"));
                        gen.writeStringField("DeliveryOrganization", reader.getAttributeValue(null, "DeliveryOrganization"));
                        gen.writeStringField("registryAmount", reader.getAttributeValue(null, "RegistrySum"));
                    } else if (name.equals("Payment")) {
                        if (!arrayStarted) {
                            gen.writeFieldName("paymentRegistryItem");
                            gen.writeStartArray();
                            arrayStarted = true;
                        }
                        gen.writeStartObject();
                        gen.writeStringField("RegistryStringUID", reader.getAttributeValue(null, "RegistryStringUID"));
                        gen.writeStringField("PaymentType", reader.getAttributeValue(null, "PaymentType"));
                        gen.writeStringField("PaymentSum", reader.getAttributeValue(null, "PaymentSum"));
                        gen.writeStringField("PaymentPurpose", reader.getAttributeValue(null, "PaymentPurpose"));
                        gen.writeStringField("PaymentOrder", reader.getAttributeValue(null, "PaymentOrder"));
                        gen.writeStringField("OperationKind", reader.getAttributeValue(null, "OperationKind"));
                        gen.writeStringField("ReceiverBankBic", reader.getAttributeValue(null, "ReceiverBankBic"));
                        gen.writeStringField("ReceiverCorrAccount", reader.getAttributeValue(null, "ReceiverCorrAccount"));
                        gen.writeStringField("ReceiverINN", reader.getAttributeValue(null, "ReceiverINN"));
                        gen.writeStringField("ReceiverAccountNumber", reader.getAttributeValue(null, "ReceiverAccountNumber"));
                        gen.writeStringField("ReceiverName", reader.getAttributeValue(null, "ReceiverName"));
                        gen.writeEndObject();
                        paymentCount++;
                        // Пример замера пика памяти каждые 100_000 платежей
                        if (paymentCount % 100_000 == 0) {
                            long currentMem = runtime.totalMemory() - runtime.freeMemory();
//                            if (currentMem > peakMemory) peakMemory = currentMem;
                            System.out.printf("Память на %d платежах: %.2f MB\n", paymentCount, currentMem / 1024.0 / 1024.0);
                        }
                    }
                }
            }
            // Закрываем массив, если был открыт
            if (arrayStarted) {
                gen.writeEndArray();
            }
            gen.writeEndObject(); // Конец корневого объекта JSON
        }
        reader.close();
        // Замер памяти и времени после парсинга
        long endTime = System.currentTimeMillis();
        long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Использовано памяти: %.2f MB\n", (usedMemoryAfter - usedMemoryBefore) / 1024.0 / 1024.0);
//        System.out.printf("Пиковое использование памяти: %.2f MB\n", (peakMemory - usedMemoryBefore) / 1024.0 / 1024.0);
        System.out.printf("Время выполнения: %.2f сек\n", (endTime - startTime) / 1000.0);
        System.out.println("JSON успешно создан (Jackson StAX): " + jsonPath);
    }

    /**
     * Выполняет GET-запрос к заданному URL и возвращает значение поля "source" из JSON-ответа.
     */
    public static String fetchSourceField(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка HTTP: " + response.statusCode());
        }
        // Парсим JSON и возвращаем поле "source"
        JsonNode root = new ObjectMapper().readTree(response.body());
        if (root.has("source")) {
            return root.get("source").asText();
        } else {
            throw new RuntimeException("Поле 'source' не найдено в ответе");
        }
    }
}
