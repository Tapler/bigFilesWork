package org.example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class XmlToJsonJacksonStaxConverterWithApi {
    public static final String msgPackageId = "7564";
    public static final boolean USE_API = true; // <-- переключатель: true = из API, false = из файла
    public static final String XML_PATH = "output_big_2000000_1.xml"; // путь к локальному XML (если не из API)

    public static void main(String[] args) throws Exception {
        // Замер памяти и времени до парсинга
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        // --- Ветка выбора источника XML ---
        InputStream xmlInputStream;
        String base64Path = "downloaded_from_api.b64"; // путь для сохранения base64
        if (USE_API) {
            // url читается из application.yml через Config
            String url = Config.get("find-msg-package-url") + msgPackageId;
            try {
                // --- Замер времени вызова API ---
                // Сохраняем base64 в файл
                saveBase64SourceToFile(url, base64Path);
                xmlInputStream = streamBase64SourceFieldToInputStream(url);
            } catch (Exception ex) {
                System.err.println("Ошибка при получении поля source: " + ex.getMessage());
                return;
            }
        } else {
            try {
                xmlInputStream = new BufferedInputStream(new FileInputStream(XML_PATH), 128 * 1024);
            } catch (Exception ex) {
                System.err.println("Ошибка при открытии XML файла: " + ex.getMessage());
                return;
            }
        }
        // Пути к результирующему JSON
        String jsonPath = "output_big_parsed_1.json";

        // Отключаем проверку SSL-сертификата
        Main.disableSslVerification();
        // Отключаем проверку совпадения имени хоста для HTTPS
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        int count = 0; // Счетчик платежей
        try {
            // --- Основной конвертер: XML → JSON (через поток, без файла) ---
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(xmlInputStream);
            JsonFactory jsonFactory = new JsonFactory();
            try (JsonGenerator jsonGenerator = jsonFactory.createGenerator(new OutputStreamWriter(new FileOutputStream(jsonPath), StandardCharsets.UTF_8))) {
                jsonGenerator.useDefaultPrettyPrinter(); // Красивое форматирование JSON
                jsonGenerator.writeStartObject(); // Начало корневого объекта JSON
                boolean arrayStarted = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getLocalName();
                        if (name.equals("PaymentsRegistrySegment")) {
                            // Корневые атрибуты XML -> поля JSON
                            jsonGenerator.writeStringField("RegistryUID", reader.getAttributeValue(null, "RegistryUID"));
                            jsonGenerator.writeStringField("RegistryDate", reader.getAttributeValue(null, "RegistryDate"));
                            jsonGenerator.writeStringField("RegistrySum", reader.getAttributeValue(null, "RegistrySum"));
                            jsonGenerator.writeStringField("RegistryType", reader.getAttributeValue(null, "RegistryType"));
                            jsonGenerator.writeStringField("DeliveryOrganization", reader.getAttributeValue(null, "DeliveryOrganization"));
                            jsonGenerator.writeStringField("registryAmount", reader.getAttributeValue(null, "RegistrySum"));
                        } else if (name.equals("Payment")) {
                            if (!arrayStarted) {
                                jsonGenerator.writeFieldName("paymentRegistryItem");
                                jsonGenerator.writeStartArray();
                                arrayStarted = true;
                            }
                            jsonGenerator.writeStartObject();
                            jsonGenerator.writeStringField("RegistryStringUID", reader.getAttributeValue(null, "RegistryStringUID"));
                            jsonGenerator.writeStringField("PaymentType", reader.getAttributeValue(null, "PaymentType"));
                            jsonGenerator.writeStringField("PaymentSum", reader.getAttributeValue(null, "PaymentSum"));
                            jsonGenerator.writeStringField("PaymentPurpose", reader.getAttributeValue(null, "PaymentPurpose"));
                            jsonGenerator.writeStringField("PaymentOrder", reader.getAttributeValue(null, "PaymentOrder"));
                            jsonGenerator.writeStringField("OperationKind", reader.getAttributeValue(null, "OperationKind"));
                            jsonGenerator.writeStringField("ReceiverBankBic", reader.getAttributeValue(null, "ReceiverBankBic"));
                            jsonGenerator.writeStringField("ReceiverCorrAccount", reader.getAttributeValue(null, "ReceiverCorrAccount"));
                            jsonGenerator.writeStringField("ReceiverINN", reader.getAttributeValue(null, "ReceiverINN"));
                            jsonGenerator.writeStringField("ReceiverAccountNumber", reader.getAttributeValue(null, "ReceiverAccountNumber"));
                            jsonGenerator.writeStringField("ReceiverName", reader.getAttributeValue(null, "ReceiverName"));
                            jsonGenerator.writeEndObject();
                            count++;
                            // Пример замера пика памяти каждые 100_000 платежей
                            if (count % 100_000 == 0) {
                                long currentMem = runtime.totalMemory() - runtime.freeMemory();
                            }
                        }
                    }
                }
                // Закрываем массив, если был открыт
                if (arrayStarted) {
                    jsonGenerator.writeEndArray();
                }
                jsonGenerator.writeEndObject(); // Конец корневого объекта JSON
            }
            reader.close();
        } finally {
            if (xmlInputStream != null) xmlInputStream.close();
        }
        // --- итоговые замеры ---
        long usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long endTime = System.currentTimeMillis();
        System.out.printf("Память на %d платежах: %.2f MB\n", count, usedMemoryAfter/1048576.0);
        System.out.printf("Использовано памяти: %.2f MB\n", (usedMemoryAfter-usedMemoryBefore)/1048576.0);
        System.out.printf("Время выполнения: %.2f сек\n", (endTime-startTime)/1000.0);
        System.out.println("JSON успешно создан (Jackson StAX): " + jsonPath);
    }

    /**
     * Получает base64-строку из поля "source" JSON-ответа API через Jackson (быстро и безопасно).
     */
    public static String fetchSourceField(String urlStr) throws Exception {
        Main.disableSslVerification();
        java.net.URL url = new java.net.URL(urlStr);
        javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) throw new RuntimeException("Ошибка HTTP: " + code);
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 128 * 1024)) {
            com.fasterxml.jackson.core.StreamReadConstraints constraints =
                com.fasterxml.jackson.core.StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build();
            com.fasterxml.jackson.core.JsonFactory factory = new com.fasterxml.jackson.core.JsonFactory();
            factory.setStreamReadConstraints(constraints);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper(factory);
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(in);
            if (root.has("source")) {
                return root.get("source").asText();
            } else {
                throw new RuntimeException("Поле 'source' не найдено в ответе");
            }
        }
    }

    /**
     * Получает InputStream, отдающий декодированный base64 XML из поля source JSON-ответа API.
     * Не создаёт временных файлов, не держит base64 в памяти.
     * Теперь использует Jackson для быстрого поиска поля source.
     */
    public static InputStream streamBase64SourceFieldToInputStream(String urlStr) throws Exception {
        String base64 = fetchSourceField(urlStr);
        // Оборачиваем base64-строку в поток и сразу декодируем
        return java.util.Base64.getDecoder().wrap(new ByteArrayInputStream(base64.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Сохраняет base64-строку поля "source" из JSON-ответа API в файл.
     * Возвращает путь к сохранённому файлу.
     */
    public static String saveBase64SourceToFile(String urlStr, String outBase64Path) throws Exception {
        Main.disableSslVerification();
        java.net.URL url = new java.net.URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) throw new RuntimeException("Ошибка HTTP: " + code);
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 128 * 1024);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(outBase64Path), 128 * 1024)) {
            String field = "\"source\"";
            int match = 0;
            boolean inString = false, found = false;
            int c;
            // Поиск поля "source"
            while ((c = in.read()) != -1) {
                if (!inString) {
                    if (c == field.charAt(match)) {
                        match++;
                        if (match == field.length()) {
                            while ((c = in.read()) != -1 && (c == ' ' || c == '\n' || c == '\r' || c == '\t')) ;
                            if (c == ':') {
                                while ((c = in.read()) != -1 && c != '"') ;
                                if (c == '"') {
                                    inString = true;
                                    found = true;
                                    break;
                                }
                            }
                            match = 0;
                        }
                    } else {
                        match = (c == field.charAt(0)) ? 1 : 0;
                    }
                }
            }
            if (!found) throw new RuntimeException("Поле 'source' не найдено в ответе");
            // Копируем base64-строку до закрывающей кавычки
            boolean escape = false;
            while ((c = in.read()) != -1) {
                if (escape) {
                    out.write(c);
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break;
                } else {
                    out.write(c);
                }
            }
        }
        return outBase64Path;
    }
}
