package org.example;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main {
    public static final int FILE_COUNT = 1; // Глобальное количество файлов (SegmentCount)
    public static final int PAYMENT_COUNT = 200000; // Глобальное количество платежей в каждом файле

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("mainSendApi")) {
            mainSendApi(args);
            return;
        }
        if (args.length > 0 && args[0].equals("sendJsonBase64")) {
            sendJsonFileAsBase64ToApi();
            return;
        }
        if (args.length > 0 && args[0].equals("sendXmlBase64")) {
            sendXmlFileAsBase64ToApi();
            return;
        }
        // Очистка файлов от прошлого запуска
        for (File f : new File(".").listFiles()) {
            String name = f.getName();
            if (name.matches("output_\\d+\\.xml") || name.equals("result2.zip") || name.equals("result2.zip.base64") || name.equals("result.base64")) {
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

        // Сборка всех output_*.xml файлов в архив result2.zip
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("result2.zip"))) {
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
        System.out.println("Все файлы собраны в архив result2.zip");

        // Запись base64 файла
        byte[] zipBytes = Files.readAllBytes(new File("result2.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        try (FileWriter writer = new FileWriter("result2.zip.base64")) {
            writer.write(base64);
        }
        System.out.println("Создан файл result2.zip.base64 с base64 содержимым архива");
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
        byte[] zipBytes = Files.readAllBytes(new File("result2.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        // URL и токен читаются из application.yml через Config
        String token = Config.get("token");
        // Формируем имя файла для API
//        String fileName = String.format("cpv_mo%d_%03d.xml.good", PAYMENT_COUNT, FILE_COUNT);
        String fileName = String.format("cpv_mo%d_%03d.xml", PAYMENT_COUNT, FILE_COUNT);
        sendBase64ToApi(base64, fileName, token, FILE_COUNT);
    }

    // Метод для отправки base64 XML-файла (result.base64) в API с isUnzip=1
    public static void sendXmlFileAsBase64ToApi() throws IOException {
        disableSslVerification();
        String xmlBase64FileName = "result.base64";
        File xmlBase64File = new File(xmlBase64FileName);
        if (!xmlBase64File.exists()) {
            System.out.println("Файл " + xmlBase64FileName + " не найден");
            return;
        }
        String base64 = new String(Files.readAllBytes(xmlBase64File.toPath()));
        String token = Config.get("token");
        // Имя файла для API (по аналогии с mainSendApi)
        String fileName = String.format("cpv_mo%d_%03d.xml", PAYMENT_COUNT, FILE_COUNT);
        sendBase64ToApi(base64, fileName, token, FILE_COUNT, 1);
    }

    public static void sendJsonFileAsBase64ToApi() throws IOException {
        disableSslVerification();
        String jsonBase64FileName = "result.base64";
        File xmlBase64File = new File(jsonBase64FileName);
        if (!xmlBase64File.exists()) {
            System.out.println("Файл " + jsonBase64FileName + " не найден");
            return;
        }
        String base64 = new String(Files.readAllBytes(xmlBase64File.toPath()));
        String token = Config.get("token");
        // Имя файла для API (по аналогии с mainSendApi)
        String fileName = String.format("cpv_mo%d_%03d.json", PAYMENT_COUNT, FILE_COUNT);
        sendBase64ToApi(base64, fileName, token, FILE_COUNT, 1);
    }

    // Метод для отправки base64 в API
    // Добавлен параметр isUnzip для поддержки разных сценариев
    public static void sendBase64ToApi(String base64, String fileName, String token, int fileCount, int isUnzip) throws IOException {
        String apiUrlMessageHub = Config.get("api-url");
        String json = "{" +
                "\"department\": \"ПАО Промсвязьбанк\"," +
                "\"destinationBusinessSystem\": \"QCPPMDB\"," +
                "\"direction\": 0," +
                "\"fileName\": \"" + fileName + "\"," +
                "\"formatName\": \"Формат обработки платежных реестров ЦПВ МО\"," +
                "\"isUnzip\": " + isUnzip + "," +
                "\"netStorage\": \"file\"," +
                "\"senderBusinessSystem\": \"QCPPMDB\"," +
                "\"noProcessPackageCreate\": true," +
                "\"source\": \"" + base64 + "\"}";
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
    }

    // Старый метод для обратной совместимости (по умолчанию isUnzip=2)
    public static void sendBase64ToApi(String base64, String fileName, String token, int fileCount) throws IOException {
        sendBase64ToApi(base64, fileName, token, fileCount, 2);
    }
}