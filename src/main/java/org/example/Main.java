package org.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONObject;
import org.json.JSONArray;

public class Main {
    public static final int FILE_COUNT = 1; // Глобальное количество файлов (SegmentCount)
    public static final int PAYMENT_COUNT = 10; // Глобальное количество платежей в каждом файле

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("mainSendApi")) {
            mainSendApi(args);
            return;
        }
        // Очистка файлов от прошлого запуска
        for (File f : new File(".").listFiles()) {
            String name = f.getName();
            if (name.matches("output_\\d+\\.xml") || name.equals("result.zip") || name.equals("result.zip.base64")) {
                f.delete();
            }
        }

        Random random = new Random();
        String[] surnames = {"Иванов", "Петров", "Сидоров", "Кузнецов", "Попов", "Соколов", "Лебедев", "Козлов", "Новиков", "Морозов", "Егоров", "Волков", "Соловьёв", "Васильев", "Зайцев", "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов", "Воробьёв", "Фёдоров", "Михайлов", "Беляев", "Тарасов", "Белов", "Комаров", "Орлов", "Киселёв", "Макаров", "Андреев"};
        String[] names = {"Иван", "Дмитрий", "Павел", "Андрей", "Николай", "Алексей", "Сергей", "Владимир", "Михаил", "Евгений", "Георгий", "Виктор", "Виталий", "Анатолий", "Юрий", "Григорий", "Станислав", "Вячеслав", "Василий", "Аркадий", "Олег", "Артур", "Руслан", "Роман", "Антон", "Игорь", "Ярослав", "Максим", "Пётр", "Егор"};
        String[] patronymics = {"Иванович", "Дмитриевич", "Павлович", "Андреевич", "Николаевич", "Алексеевич", "Сергеевич", "Владимирович", "Михайлович", "Евгеньевич", "Георгиевич", "Викторович", "Витальевич", "Анатольевич", "Юрьевич", "Григорьевич", "Станиславович", "Вячеславович", "Васильевич", "Аркадьевич", "Олегович", "Артурович", "Русланович", "Романович", "Антонович", "Игоревич", "Ярославович", "Максимович", "Петрович", "Егорович"};

        // количество платежей в каждом файле
        int fileCount = FILE_COUNT; // Количество файлов (SegmentCount)
        int paymentIndex = 1;
        for (int fileNum = 1; fileNum <= fileCount; fileNum++) {
            List<Payment> payments = new ArrayList<>();
            for (int i = 0; i < PAYMENT_COUNT; i++, paymentIndex++) {
                String paymentPurpose = "Пенсия " + paymentIndex;
                String receiverBankBic = String.format("%09d", random.nextInt(1_000_000_000));
                String receiverCorrAccount = "30101810400000000705";
                String receiverINN = String.format("%014d", Math.abs(random.nextLong()) % 1_000_000_000_000_000L); // 14 цифр
                String receiverAccountNumber = "00012298253454792492";
                String receiverName = surnames[random.nextInt(30)] + " " + names[random.nextInt(30)] + " " + patronymics[random.nextInt(30)];
                payments.add(new Payment(
                        UUID.randomUUID().toString(),
                        "SocBenefit",
                        30000 + random.nextInt(401) * 100,
                        paymentPurpose,
                        "01",
                        "01",
                        receiverBankBic,
                        receiverCorrAccount,
                        receiverINN,
                        receiverAccountNumber,
                        receiverName
                ));
            }
            long sum = payments.stream().mapToLong(Payment::getPaymentSum).sum();
            String registryUID = UUID.randomUUID().toString();
            String registryDate = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<PaymentsRegistrySegment xmlns=\"\"\n" +
                    "  RegistryUID=\"" + registryUID + "\"\n" +
                    "  RegistryDate=\"" + registryDate + "\"\n" +
                    "  RegistrySum=\"" + sum + "\"\n" +
                    "  SegmentNumber=\"" + fileNum + "\"\n" +
                    "  SegmentCount=\"" + fileCount + "\"\n" +
                    "  RegistryType=\"Collecting\"\n" +
                    "  DeliveryOrganization=\"PSB\"\n" +
                    "  SegmentSum=\"" + sum + "\">\n";
            for (Payment p : payments) {
                xml += p.toXml() + "\n";
            }
            xml += "</PaymentsRegistrySegment>";
            try (FileWriter writer = new FileWriter("output_" + fileNum + ".xml")) {
                writer.write(xml);
            }
        }
        System.out.println("XML файлы успешно сгенерированы: output_1.xml ... output_" + fileCount + ".xml");

        // Сборка всех output_*.xml файлов в архив result.zip
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("result.zip"))) {
            for (int fileNum = 1; fileNum <= fileCount; fileNum++) {
                String fileName = "output_" + fileNum + ".xml";
                File file = new File(fileName);
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
        System.out.println("Все файлы собраны в архив result.zip");

        // Запись base64 файла
        byte[] zipBytes = Files.readAllBytes(new File("result.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        try (FileWriter writer = new FileWriter("result.zip.base64")) {
            writer.write(base64);
        }
        System.out.println("Создан файл result.zip.base64 с base64 содержимым архива");
    }

    // Отключение проверки SSL-сертификата (НЕ для продакшена)
    public static void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Метод для запуска отправки base64 в API отдельно
    public static void mainSendApi(String[] args) throws IOException {
        disableSslVerification();
        byte[] zipBytes = Files.readAllBytes(new File("result.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        // URL и токен читаются из application.yml через Config
        String token = Config.get("token");
        // Формируем имя файла для API
        String fileName = String.format("cpv_mo%d_%03d.xml.good", PAYMENT_COUNT, FILE_COUNT);
        sendBase64ToApi(base64, fileName, token, FILE_COUNT);
    }

    // Метод для отправки base64 в API
    public static void sendBase64ToApi(String base64, String fileName, String token, int fileCount) throws IOException {
        // URL и токен читаются из application.yml через Config
        String apiUrlMessageHub = Config.get("api-url");
        String json = "{"
                + "\"department\": \"ПАО Промсвязьбанк\"," 
                + "\"destinationBusinessSystem\": \"QCPPMD\"," 
                + "\"direction\": 0," 
                + "\"fileName\": \"" + fileName + "\"," 
                + "\"formatName\": \"Формат обработки платежных реестров ЦПВ МО\"," 
                + "\"isUnzip\": 2," 
                + "\"netStorage\": \"file\"," 
                + "\"senderBusinessSystem\": \"ГИИС ДМДК\"," 
                + "\"noProcessPackageCreate\": true," 
                + "\"source\": \"" + base64 + "\"}";
//        // Сохраняем requestBody (json) в файл для отладки
//        try (java.io.FileWriter fw = new java.io.FileWriter("requestBody.json")) {
//            fw.write(json);
//        }
        URL url = new URL(apiUrlMessageHub);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("accept", "*/*");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes("UTF-8");
            os.write(input, 0, input.length);
        }
        int code = conn.getResponseCode();
        StringBuilder response = new StringBuilder();
        try (java.io.InputStream is = (code < 400 ? conn.getInputStream() : conn.getErrorStream())) {
            int ch;
            while ((ch = is.read()) != -1) {
                response.append((char) ch);
            }
        }
        System.out.println("API Response (" + code + "): " + response);
        // Проверка childMsgPackageId
        if (code == 200) {
            try {
                JSONObject obj = new JSONObject(response.toString());
                if (obj.has("childMsgPackageId")) {
                    JSONArray arr = obj.getJSONArray("childMsgPackageId");
                    if (arr.length() == fileCount) {
                        System.out.println("Проверка успешна: childMsgPackageId содержит " + fileCount + " элементов.");
                    } else {
                        System.out.println("Ошибка: childMsgPackageId содержит " + arr.length() + " элементов, ожидалось: " + fileCount);
                    }
                } else {
                    System.out.println("Ошибка: поле childMsgPackageId отсутствует в ответе API");
                }
            } catch (Exception e) {
                System.out.println("Ошибка при разборе ответа API: " + e.getMessage());
            }
        }
    }
}