package org.example;

import java.io.*;
import java.nio.file.Files;
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

public class GenerateBig {
    // Количество файлов (сегментов) и платежей в каждом файле настраиваются здесь:
    public static final int FILE_COUNT = 1; // Глобальное количество файлов (SegmentCount)
        public static final int PAYMENT_COUNT = 1; // Глобальное количество платежей в каждом файле

    public static void main(String[] args) throws IOException {
        // Очистка файлов от прошлого запуска
        for (File f : new File(".").listFiles()) {
            String name = f.getName();
            if (name.matches("output_.*\\.xml") || name.matches("result.*\\.zip") || name.matches("result.*\\.base64")) {
                f.delete();
            }
        }

        Random random = new Random();
        String[] surnames = {"Иванов", "Петров", "Сидоров", "Кузнецов", "Попов", "Соколов", "Лебедев", "Козлов", "Новиков", "Морозов", "Егоров", "Волков", "Соловьёв", "Васильев", "Зайцев", "Павлов", "Семёнов", "Голубев", "Виноградов", "Богданов", "Воробьёв", "Фёдоров", "Михайлов", "Беляев", "Тарасов", "Белов", "Комаров", "Орлов", "Киселёв", "Макаров", "Андреев"};
        String[] names = {"Иван", "Дмитрий", "Павел", "Андрей", "Николай", "Алексей", "Сергей", "Владимир", "Михаил", "Евгений", "Георгий", "Виктор", "Виталий", "Анатолий", "Юрий", "Григорий", "Станислав", "Вячеслав", "Василий", "Аркадий", "Олег", "Артур", "Руслан", "Роман", "Антон", "Игорь", "Ярослав", "Максим", "Пётр", "Егор"};
        String[] patronymics = {"Иванович", "Дмитриевич", "Павлович", "Андреевич", "Николаевич", "Алексеевич", "Сергеевич", "Владимирович", "Михайлович", "Евгеньевич", "Георгиевич", "Викторович", "Витальевич", "Анатольевич", "Юрьевич", "Григорьевич", "Станиславович", "Вячеславович", "Васильевич", "Аркадьевич", "Олегович", "Артурович", "Русланович", "Романович", "Антонович", "Игоревич", "Ярославович", "Максимович", "Петрович", "Егорович"};

        // Фиксированная часть ReceiverAccountNumber (первые 15 символов из примера)
        String baseAccountNumber = "408178104505900"; // Можно взять любую из вашего списка

        // Двойной проход: сначала считаем сумму, потом генерируем XML
        // Новый подход: сначала считаем сумму всех SegmentSum (RegistrySum)
        List<List<Integer>> allPaymentSums = new ArrayList<>();
        long registrySum = 0;
        for (int fileNum = 1; fileNum <= FILE_COUNT; fileNum++) {
            List<Integer> paymentSums = new ArrayList<>();
            long segmentSum = 0;
            for (int i = 1; i <= PAYMENT_COUNT; i++) {
                int paymentSum = 30000 + random.nextInt(401) * 100;
                paymentSums.add(paymentSum);
                segmentSum += paymentSum;
            }
            allPaymentSums.add(paymentSums);
            registrySum += segmentSum;
        }

        // Второй проход: генерация XML с правильным RegistrySum
        for (int fileNum = 1; fileNum <= FILE_COUNT; fileNum++) {
            List<Integer> paymentSums = allPaymentSums.get(fileNum - 1);
            long segmentSum = paymentSums.stream().mapToLong(Integer::longValue).sum();

            String registryUID = UUID.randomUUID().toString();
            String registryDate = ZonedDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
            String fileName = "cpv_mo" + PAYMENT_COUNT + fileNum + ".xml";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                // Запись заголовка XML с RegistrySum как сумма всех SegmentSum
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<PaymentsRegistrySegment xmlns=\"\"\n");
                writer.write("  RegistryUID=\"" + registryUID + "\"\n");
                writer.write("  RegistryDate=\"" + registryDate + "\"\n");
                writer.write("  RegistrySum=\"" + registrySum + "\"\n");
                writer.write("  SegmentNumber=\"" + fileNum + "\"\n");
                writer.write("  SegmentCount=\"" + FILE_COUNT + "\"\n");
                writer.write("  RegistryType=\"Collecting\"\n");
                writer.write("  DeliveryOrganization=\"PSB\"\n");
                writer.write("  SegmentSum=\"" + segmentSum + "\">\n");

                // Генерация и запись платежей
                for (int i = 1; i <= PAYMENT_COUNT; i++) {
                    String paymentPurpose = "Пенсия " + ((fileNum - 1) * PAYMENT_COUNT + i);
                    // Вместо случайного BIC теперь выбирается один из трех по заданной вероятности:
                    // 044525555 (ПСБ) — 80%, 044525201 (Авангард) — 10%, 044525787 (УралСИБ) — 10%
                    int bicRand = random.nextInt(100);
                    String receiverBankBic;
                    if (bicRand < 80) {
                        receiverBankBic = "044525555"; // ПСБ
                    } else if (bicRand < 90) {
                        receiverBankBic = "044525201"; // Авангард
                    } else {
                        receiverBankBic = "044525787"; // УралСИБ
                    }
                    int randomSuffix = 10000 + random.nextInt(90000); // 5 случайных цифр
                    String receiverAccountNumber = baseAccountNumber + randomSuffix;
                    String receiverCorrAccount = "30101810400000000705";
                    String receiverINN = String.format("%014d", Math.abs(random.nextLong()) % 1_000_000_000_000_000L);
                    String receiverName = surnames[random.nextInt(30)] + " " + names[random.nextInt(30)] + " " + patronymics[random.nextInt(30)];
                    int paymentSum = paymentSums.get(i - 1);

                    Payment payment = new Payment(
                            UUID.randomUUID().toString(),
                            "SocBenefit",
                            paymentSum,
                            paymentPurpose,
                            "01",
                            "01",
                            receiverBankBic,
                            receiverCorrAccount,
                            receiverINN,
                            receiverAccountNumber,
                            receiverName
                    );
                    writer.write(payment.toXml());
                    writer.write("\n");
                }

                writer.write("</PaymentsRegistrySegment>");
            }
            System.out.println("Файл " + fileName + " сгенерирован. SegmentSum: " + segmentSum);
        }

        // Сборка всех cpv_mo_*.xml файлов в архив cpv_mo1.zip
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream("cpv_mo1.zip"))) {
            for (int fileNum = 1; fileNum <= FILE_COUNT; fileNum++) {
                String fileName = "cpv_mo" + PAYMENT_COUNT + fileNum + ".xml";
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
        System.out.println("Все файлы собраны в архив cpv_mo1.zip");

        // Запись base64 файла
        byte[] zipBytes = Files.readAllBytes(new File("cpv_mo1.zip").toPath());
        String base64 = Base64.getEncoder().encodeToString(zipBytes);
        try (FileWriter writer = new FileWriter("cpv_mo1.zip.base64")) {
            writer.write(base64);
        }
        System.out.println("Создан файл cpv_mo1.zip.base64 с base64 содержимым архива");

        // Если файлов только один, сохраняем base64 именно XML-файла, а не архива
        if (FILE_COUNT == 1) {
            String xmlFileName = "cpv_mo" + PAYMENT_COUNT + "1.xml";
            byte[] xmlBytes = Files.readAllBytes(new File(xmlFileName).toPath());
            String xmlBase64 = Base64.getEncoder().encodeToString(xmlBytes);
            // результат в формате base64 сохраняется как result.base64
            try (FileWriter writer = new FileWriter("result.base64")) {
                writer.write(xmlBase64);
            }
            System.out.println("Создан файл result.base64 с base64 содержимым XML-файла (только если FILE_COUNT == 1)");
        }
    }
}